package io.github.youndie.kompot.spec

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// Every object of a JSON tree, the root included. Both the conformance checks and the corpus tests
// need it: a rule about a component — a non-empty id, a fieldId that resolves — applies to every
// node rather than to the root alone.
public fun collectJsonObjects(element: JsonElement): List<JsonObject> =
    when (element) {
        is JsonObject -> listOf(element) + element.values.flatMap { collectJsonObjects(it) }
        is JsonArray -> element.flatMap { collectJsonObjects(it) }
        else -> emptyList()
    }

// The same objects, each with the path it was found at.
//
// The path is a JsonPath — the very type a finding carries — rather than the string it prints as.
// That is the whole reason to have it: a rule reporting about a node and a validator reporting about
// the same node then produce the same value, not two strings that happen to match today.
public data class JsonNode(
    val path: JsonPath,
    val value: JsonObject,
)

// STRUCTURAL, and deliberately not schema-driven. It would be natural to descend only into the slots
// a component declares (see childSlots below) — and it would make the traversal exactly as complete
// as the schema is. A node of a type nobody described, a body shaped by an envelope, a property added
// to a deployment's component last week: each is a place where a schema-driven walk goes quietly
// blind, and "quietly" is the problem. The schema says what a child SLOT is; it does not get to say
// what exists.
public fun walkJsonObjects(element: JsonElement): Sequence<JsonNode> = walk(element, JsonPath.ROOT)

private fun walk(
    element: JsonElement,
    path: JsonPath,
): Sequence<JsonNode> =
    when (element) {
        is JsonObject ->
            sequenceOf(JsonNode(path, element)) +
                element.entries.asSequence().flatMap { (name, value) -> walk(value, path + name) }

        is JsonArray ->
            element.asSequence().flatMapIndexed { index, value -> walk(value, path + index) }

        else -> emptySequence()
    }

// A property of a component that holds other components.
//
// `many` and `required` come from the schema rather than from a convention: a tree editor has to know
// whether a slot takes one node or a list before it can offer to add to it, and whether emptying it is
// allowed before it offers to remove.
public data class Slot(
    val name: String,
    val many: Boolean,
    val required: Boolean,
)

// WHICH PROPERTIES OF A COMPONENT HOLD COMPONENTS, derived from the generated schema.
//
// Nesting is a convention of each type and of nothing else: `column.children`, `row.children`,
// `paginated_list.initialItems` AND `emptyState`, `wizard_screen.content`, a deployment's
// `surface.children` — while `table.rows` holds rows and `bottom_nav.items` holds no components at
// all. Every consumer that has needed this list has kept it by hand, and konekt's own note says five
// copies of it existed and each went stale separately: the walk that did not know about `emptyState`
// was blind exactly when the screen had one thing on it.
//
// The schema already knows. A property whose $ref — directly, through `items`, or through the
// `anyOf` a nullable field is printed as — points at the component hierarchy is a slot, and a list
// derived from a generated artefact cannot fall behind the types it is generated from.
//
// The closed list comes from the PROFILE's discriminator mapping rather than from a scan of every
// `$defs` entry that looks like a component: the profile is the set of types a particular build can
// actually receive, which is the question a caller is really asking, and a scan would answer a
// different one — "types that happen to be on the classpath".
public fun childSlots(
    schemas: Map<String, JsonObject>,
    hierarchy: String = KompotProtocol.COMPONENT_HIERARCHY,
): Map<String, List<Slot>> {
    val profile =
        schemas[KompotProtocol.PROFILE_FILE_NAME]
            ?: error(
                "childSlots needs the profile (${KompotProtocol.PROFILE_FILE_NAME}): it carries the closed list of " +
                    "types this build can receive, and the module schemas alone do not.",
            )

    val base =
        (profile["\$defs"] as? JsonObject)?.get(hierarchy)?.jsonObject
            ?: error("the profile declares no hierarchy \"$hierarchy\"")

    val mapping =
        (base["discriminator"] as? JsonObject)?.get("mapping")?.jsonObject
            ?: error("the hierarchy \"$hierarchy\" carries no discriminator mapping")

    return mapping.entries.associate { (wireType, reference) ->
        val definition = resolve((reference as JsonPrimitive).content, schemas)
        val required =
            (definition["required"] as? JsonArray).orEmpty().map { it.jsonPrimitive.content }.toSet()

        wireType to
            (definition["properties"] as? JsonObject)
                .orEmpty()
                .mapNotNull { (name, property) ->
                    slotFor(name, property.jsonObject, hierarchy, name in required)
                }
    }
}

// WHICH TYPES CAN ASK FOR MORE OF THEMSELVES: the wire types whose definition names the page-load
// action somewhere in its properties.
//
// Derived rather than the one name everybody knows, and for the reason the child slots are: a
// deployment may put a list of its own beside the standard one, and it will reuse this action because
// that is the only thing a client's list renderer knows how to call. A hard-coded "paginated_list"
// would be right until the day it is not, and it would be wrong silently.
public fun paginatingTypes(
    schemas: Map<String, JsonObject>,
    hierarchy: String = KompotProtocol.COMPONENT_HIERARCHY,
): Set<String> {
    val profile = schemas[KompotProtocol.PROFILE_FILE_NAME] ?: return emptySet()
    val base = (profile["\$defs"] as? JsonObject)?.get(hierarchy)?.jsonObject ?: return emptySet()
    val mapping = (base["discriminator"] as? JsonObject)?.get("mapping")?.jsonObject ?: return emptySet()

    return mapping.entries
        .filter { (_, reference) ->
            val definition = resolve((reference as JsonPrimitive).content, schemas)
            // The whole definition as text: the reference may sit under `anyOf` for a nullable
            // property or under `items` for a list of them, and the question here is only whether the
            // type mentions it at all.
            LOAD_PAGE in definition.toString()
        }.map { it.key }
        .toSet()
}

private const val LOAD_PAGE = "\$defs/LoadPage"

private fun slotFor(
    name: String,
    property: JsonObject,
    hierarchy: String,
    required: Boolean,
): Slot? {
    // A nullable property is printed as `anyOf: [<the type>, {type: null}]`, so the type has to be
    // dug out of the branches. Taking the first non-null branch and not merging them: the generator
    // prints exactly one.
    val unwrapped =
        (property["anyOf"] as? JsonArray ?: property["oneOf"] as? JsonArray)
            ?.map { it.jsonObject }
            ?.firstOrNull { branch -> branch["type"]?.let { (it as? JsonPrimitive)?.content } != "null" }
            ?: property

    val many = (unwrapped["type"] as? JsonPrimitive)?.content == "array"
    val leaf = if (many) unwrapped["items"]?.jsonObject ?: return null else unwrapped

    val reference = (leaf["\$ref"] as? JsonPrimitive)?.content ?: return null
    if (!reference.endsWith("#/\$defs/$hierarchy") && !reference.endsWith("/$hierarchy")) return null

    return Slot(name = name, many = many, required = required)
}

// "file.schema.json#/$defs/Key", or "#/$defs/Key" for a reference inside the document being read.
// The profile always names the file, which is why the second form does not need the current file
// threaded through here.
private fun resolve(
    reference: String,
    schemas: Map<String, JsonObject>,
): JsonObject {
    val file = reference.substringBefore('#')
    val key = reference.substringAfterLast('/')
    val document = schemas[file] ?: error("the profile refers to an unknown schema file \"$file\"")
    return (document["\$defs"] as? JsonObject)?.get(key)?.jsonObject
        ?: error("the schema \"$file\" declares no \$defs/$key")
}

private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()

private fun JsonObject?.orEmpty(): Map<String, JsonElement> = this ?: emptyMap()
