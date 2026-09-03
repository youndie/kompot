package io.github.youndie.kompot.spec

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

// A validator for exactly the subset of JSON Schema 2020-12 that KompotSchemaGenerator prints:
// $ref (across files too), type, const, enum, pattern, not, properties, required, items,
// additionalProperties, oneOf/anyOf and discriminator.
//
// An implementation of our own rather than a library, deliberately: the subset is small and entirely
// under our control, and pulling a JSON Schema validator into a toolkit for the sake of tests costs
// more than these hundred and fifty lines. A conformance kit run against SOMEONE ELSE'S server is a
// different matter — there the schema is external input rather than our own output, and a full
// validator earns its place.
// WHERE A FINDING IS, kept rather than printed away.
//
// The validator computes the path while it descends — it always did — and then glued it into the
// front of a message. That is enough for a report a person reads, and useless to anything that has
// to point at the node: a studio highlighting a tree row, an editor putting the caret on a line. The
// only way back was to parse the validator's own prefix out of its own sentence.
//
// Segments rather than the rendered string alone, and typed rather than a List<Any> of names and
// indices: a caller matching a node walks segments, and a mixed list makes every use a cast.
public class JsonPath private constructor(
    public val segments: List<Segment>,
) {
    public sealed interface Segment {
        public data class Name(val name: String) : Segment

        public data class Index(val index: Int) : Segment
    }

    public operator fun plus(name: String): JsonPath = JsonPath(segments + Segment.Name(name))

    public operator fun plus(index: Int): JsonPath = JsonPath(segments + Segment.Index(index))

    // Another path's segments hung off this one. A rule that walked a SUBTREE — the `screen` half of a
    // form response, say — has paths relative to that subtree, and reporting them as they are would
    // point at `$.children[0]` in a body whose node is at `$.screen.children[0]`.
    public fun append(other: JsonPath): JsonPath = JsonPath(segments + other.segments)

    // The notation the messages have always used, and the one a tree node carries, so a finding and a
    // node line up by string when a caller has no reason to walk segments.
    override fun toString(): String =
        buildString {
            append('$')
            segments.forEach { segment ->
                when (segment) {
                    is Segment.Name -> append('.').append(segment.name)
                    is Segment.Index -> append('[').append(segment.index).append(']')
                }
            }
        }

    override fun equals(other: Any?): Boolean = other is JsonPath && segments == other.segments

    override fun hashCode(): Int = segments.hashCode()

    public companion object {
        public val ROOT: JsonPath = JsonPath(emptyList())
    }
}

// One thing the body got wrong.
//
// `keyword` is the JSON Schema keyword that refused it — `required`, `type`, `pattern`,
// `discriminator` — because a caller grouping findings, or choosing an icon for one, otherwise has to
// read English. No severity: a schema has none, and inventing one here would be this file deciding
// which of a deployment's mistakes matter.
public data class SchemaFinding(
    val path: JsonPath,
    val keyword: String,
    val message: String,
) {
    // The line the TCK has always printed, so a report reads the same and no expectation moves.
    override fun toString(): String = "$path: $message"
}

public class JsonSchemaValidator(
    private val documents: Map<String, JsonObject>,
    // Strict mode: definitions from the profile stand in for open bases (x-kompot-open: true) while
    // resolving a $ref. It is needed because nested nodes of a tree refer to the OPEN KompotComponent
    // base by contract — otherwise a module's schema would be tied to one particular profile — while a
    // test wants the whole tree checked against a closed list of types, not just its root. Nothing has
    // to be substituted inside the .schema.json files themselves.
    private val strictProfile: JsonObject? = null,
    // Wire types that a particular DEPLOYMENT adds on top of the profile: the actions and components
    // of product features. The profile stays the toolkit's vocabulary and knows nothing about them, or
    // the protocol would start depending on product modules.
    //
    // A declared extension passes the check but its SHAPE is not validated — the validator has no
    // schema for it. That is safe precisely because an unfamiliar type degrades by protocol (see
    // x-kompot-degrades): an implementation that knows nothing about the extension will not take the
    // screen down. An undeclared type is still a violation, or the check would stop meaning anything.
    private val extensionTypes: Set<String> = emptySet(),
) {
    private val overrides: Map<String, JsonObject> =
        (strictProfile?.get("\$defs") as? JsonObject)
            ?.mapValues { (_, value) -> value.jsonObject }
            .orEmpty()

    public fun validate(
        value: JsonElement,
        ref: String,
    ): List<SchemaFinding> {
        val findings = mutableListOf<SchemaFinding>()
        val resolved = resolve(ref, currentFile = "")
        check(value, resolved.schema, resolved.file, path = JsonPath.ROOT, findings)
        return findings
    }

    private data class Resolved(
        val schema: JsonObject,
        val file: String,
        val key: String,
    )

    private fun resolve(
        ref: String,
        currentFile: String,
    ): Resolved {
        val file = ref.substringBefore('#').ifEmpty { currentFile }
        val document = documents[file] ?: error("The schema refers to an unknown file \"$file\" (\$ref = $ref)")
        var node: JsonElement = document
        var lastSegment = ""
        ref.substringAfter('#').split('/').filter { it.isNotEmpty() }.forEach { segment ->
            lastSegment = segment.replace("~1", "/").replace("~0", "~")
            node = (node as? JsonObject)?.get(lastSegment) ?: error("Unresolvable \$ref: $ref (no segment \"$lastSegment\")")
        }
        return Resolved(node.jsonObject, file, lastSegment)
    }

    private fun check(
        value: JsonElement,
        schema: JsonObject,
        file: String,
        path: JsonPath,
        findings: MutableList<SchemaFinding>,
    ) {
        (schema["\$ref"] as? JsonPrimitive)?.let { refNode ->
            val resolved = resolve(refNode.content, file)
            val strict = overrides[resolved.key].takeIf { resolved.schema["x-kompot-open"] == JsonPrimitive(true) }
            if (strict != null) {
                check(value, strict, KompotProtocol.PROFILE_FILE_NAME, path, findings)
            } else {
                check(value, resolved.schema, resolved.file, path, findings)
            }
            return
        }

        (schema["oneOf"] as? JsonArray)?.let { branches ->
            checkBranches(value, schema, branches, file, path, findings, exclusive = true)
            return
        }
        (schema["anyOf"] as? JsonArray)?.let { branches ->
            checkBranches(value, schema, branches, file, path, findings, exclusive = false)
            return
        }

        schema["type"]?.let { expected ->
            val allowed =
                when (expected) {
                    is JsonArray -> expected.map { (it as JsonPrimitive).content }
                    else -> listOf((expected as JsonPrimitive).content)
                }
            val actual = jsonTypeOf(value)
            if (allowed.none { it == actual || (it == "number" && actual == "integer") }) {
                findings += SchemaFinding(path, "type", "expected type ${allowed.joinToString("|")}, got $actual")
                return
            }
        }

        schema["const"]?.let { expected ->
            if (value != expected) findings += SchemaFinding(path, "const", "expected $expected, got $value")
        }

        (schema["enum"] as? JsonArray)?.let { options ->
            if (value !in options) findings += SchemaFinding(path, "enum", "value $value is outside the enum $options")
        }

        // `pattern` applies to strings only: for a null (a nullable field) it is silently skipped, as
        // JSON Schema requires.
        (schema["pattern"] as? JsonPrimitive)?.let { pattern ->
            val text = (value as? JsonPrimitive)?.takeIf { it.isString }?.content
            if (text != null && !Regex(pattern.content).containsMatchIn(text)) {
                findings += SchemaFinding(path, "pattern", "\"$text\" does not match the format ${pattern.content}")
            }
        }

        // `not` carries exactly one rule today, and it is here rather than inside `pattern` for a reason
        // worth keeping written down: "a deeplink is not a web address" used to be a negative lookahead,
        // which RE2 engines — Go's standard regexp among them — refuse to compile rather than degrade.
        // Expressed as a schema keyword instead of as regex syntax, the same rule compiles everywhere.
        (schema["not"] as? JsonObject)?.let { forbidden ->
            val ifItMatched = mutableListOf<SchemaFinding>()
            check(value, forbidden, file, path, ifItMatched)
            if (ifItMatched.isEmpty()) {
                findings += SchemaFinding(path, "not", "$value matches a schema it is forbidden to match ($forbidden)")
            }
        }

        if (value is JsonObject) checkObject(value, schema, file, path, findings)
        if (value is JsonArray) {
            (schema["items"] as? JsonObject)?.let { items ->
                value.forEachIndexed { index, element -> check(element, items, file, path + index, findings) }
            }
        }
    }

    // For a polymorphic branch the discriminator gives a precise diagnosis: without it, any error
    // inside a variant collapses into a useless "none of the N variants matched".
    private fun checkBranches(
        value: JsonElement,
        schema: JsonObject,
        branches: JsonArray,
        file: String,
        path: JsonPath,
        findings: MutableList<SchemaFinding>,
        exclusive: Boolean,
    ) {
        val mapping = (schema["discriminator"] as? JsonObject)?.get("mapping") as? JsonObject
        if (mapping != null) {
            val discriminator = (value as? JsonObject)?.get(KompotProtocol.DISCRIMINATOR) as? JsonPrimitive
            if (discriminator == null) {
                findings +=
                    SchemaFinding(
                        path,
                        "discriminator",
                        "no discriminator property \"${KompotProtocol.DISCRIMINATOR}\"",
                    )
                return
            }
            val target = mapping[discriminator.content] as? JsonPrimitive
            if (target == null) {
                // A declared deployment extension: there is nothing to check its shape against, and it
                // is not a violation either (see extensionTypes).
                if (discriminator.content in extensionTypes) return
                findings +=
                    SchemaFinding(
                        path,
                        "discriminator",
                        "type \"${discriminator.content}\" is in neither the profile ${mapping.keys.sorted()} " +
                            "nor the declared extensions ${extensionTypes.sorted()}",
                    )
                return
            }
            val resolved = resolve(target.content, file)
            check(value, resolved.schema, resolved.file, path, findings)
            return
        }

        val matched =
            branches.count { branch ->
                val branchFindings = mutableListOf<SchemaFinding>()
                check(value, branch.jsonObject, file, path, branchFindings)
                branchFindings.isEmpty()
            }
        if (matched == 0) {
            findings += SchemaFinding(path, "oneOf", "the value matched none of the ${branches.size} variants")
        } else if (exclusive && matched > 1) {
            findings +=
                SchemaFinding(path, "oneOf", "the value matched $matched oneOf variants, exactly one was expected")
        }
    }

    private fun checkObject(
        value: JsonObject,
        schema: JsonObject,
        file: String,
        path: JsonPath,
        findings: MutableList<SchemaFinding>,
    ) {
        val properties = schema["properties"] as? JsonObject
        (schema["required"] as? JsonArray)?.forEach { required ->
            val name = (required as JsonPrimitive).content
            if (name !in value) findings += SchemaFinding(path, "required", "required property \"$name\" is missing")
        }
        value.forEach { (name, element) ->
            val propertySchema = properties?.get(name) as? JsonObject
            if (propertySchema != null) {
                check(element, propertySchema, file, path + name, findings)
                return@forEach
            }
            when (val additional = schema["additionalProperties"]) {
                is JsonObject -> check(element, additional, file, path + name, findings)
                is JsonPrimitive ->
                    if (additional.booleanOrNull == false) {
                        findings += SchemaFinding(path, "additionalProperties", "unknown property \"$name\"")
                    }

                else -> Unit
            }
        }
    }

    private fun jsonTypeOf(value: JsonElement): String =
        when {
            value is JsonNull -> "null"
            value is JsonArray -> "array"
            value is JsonObject -> "object"
            value is JsonPrimitive && value.isString -> "string"
            value is JsonPrimitive && value.booleanOrNull != null -> "boolean"
            value is JsonPrimitive && value.longOrNull != null -> "integer"
            value is JsonPrimitive && value.doubleOrNull != null -> "number"
            else -> "string"
        }
}
