package io.github.youndie.kompot.spec

import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

// One spec module == one schema file == one Gradle module. The split follows the repository's own
// modularity on purpose: a server implementation elsewhere must be able to say "I speak kompot-core
// and kompot-standard but not your catalogue plug-in" — the same "open core plus plug-ins" division
// the Kotlin modules have.
public data class KompotSpecModule(
    val name: String,
    val description: String,
    // Open hierarchies: their members are collected from here through dumpTo.
    val serializersModule: SerializersModule = EmptySerializersModule(),
    // Entry points dumpTo cannot reach: sealed hierarchies, which are never registered in a
    // SerializersModule, and response envelopes, which are not polymorphic themselves.
    val roots: List<SerialDescriptor> = emptyList(),
    // Parts of the schema that are NOT derived from descriptors, because they describe the protocol's
    // openness rule rather than the structure of a type (see openHierarchy below).
    val handWritten: Map<String, JsonObject> = emptyMap(),
    // Constraints on GENERATED properties: defKey -> property name -> keywords to add. Needed wherever
    // the Kotlin type of a property (usually String) says nothing about its allowed format.
    val annotations: Map<String, Map<String, JsonObject>> = emptyMap(),
    // The property a polymorphic value carries its wire name in. Everything on the kompot wire uses
    // "type" and no module should change it — this exists for a schema of something that is NOT the
    // wire, and there is exactly one: the client corpus, whose steps are discriminated by "step" so
    // that a step's own key does not read like the "type" of the value inside it.
    val discriminator: String = KompotProtocol.DISCRIMINATOR,
    // Whether an object in this module may carry properties the schema does not list. True for
    // anything on the wire — §3 and §15 require a reader to ignore what it does not know — and false
    // for a format whose reader must instead refuse it. Only the client corpus is the second kind.
    val openObjects: Boolean = true,
)

// Assembling a spec from an ordered list of modules. The list belongs to a PARTICULAR BUILD rather
// than to the toolkit: only an application knows the set of types that can really travel on its wire,
// and only it knows where its own modules sit among the toolkit's.
public object KompotSpec {
    public fun generateAll(modules: List<KompotSpecModule>): List<GeneratedSchema> {
        val external = mutableMapOf<String, String>()
        return modules.map { module ->
            KompotSchemaGenerator(module, external.toMap()).generate().also { generated ->
                generated.defKeys.forEach { key -> external.putIfAbsent(key, generated.fileName) }
            }
        }
    }

    // The profile is the closed list of what a particular build actually supports. Module schemas
    // describe polymorphic bases as OPEN — any object with a "type" — because that is the runtime
    // contract, at least for KompotComponent and KompotAction. Accepting someone else's server also
    // needs the strict view, "exactly these types are allowed in this profile", and it is assembled here
    // from the x-kompot-contributes of every module.
    // Wire types a DEPLOYMENT adds on top of its modules, by hierarchy: "KompotComponent" to
    // ("banking_story"). They reach the profile as names without a shape, which is the whole of what
    // §2.4 permits — the protocol must not start depending on product modules — but as a real oneOf
    // branch rather than as a keyword only this toolkit understands. That is the difference the
    // report was about: an ordinary JSON Schema library accepts a declared extension and rejects an
    // undeclared one, with no Kotlin and no validator of ours in the picture.
    public fun profile(
        schemas: List<GeneratedSchema>,
        extensions: Map<String, Set<String>> = emptyMap(),
    ): JsonObject {
        val merged = sortedMapOf<String, MutableMap<String, String>>()
        schemas.forEach { schema ->
            schema.contributions.forEach { (hierarchy, members) ->
                val target = merged.getOrPut(hierarchy) { sortedMapOf() }
                members.forEach { (wireName, key) ->
                    val previous = target.put(wireName, "${schema.fileName}#/\$defs/$key")
                    check(previous == null) { "Wire type \"$wireName\" is declared twice in the $hierarchy hierarchy" }
                }
            }
        }

        // Whether a hierarchy degrades is stated by the module that OWNS the open base, and nowhere
        // else. Reading it here rather than restating it is the point: the profile's copy cannot drift
        // from the module's, and a reader who follows a mention of x-kompot-degrades inside the profile
        // finds the keyword in the file they are already holding.
        val degrades: Map<String, Boolean> =
            schemas
                .flatMap { schema ->
                    (schema.document["\$defs"] as? JsonObject).orEmpty().mapNotNull { (key, definition) ->
                        ((definition as? JsonObject)?.get("x-kompot-degrades") as? JsonPrimitive)
                            ?.booleanOrNull
                            ?.let { key to it }
                    }
                }.toMap()

        return buildJsonObject {
            put("\$schema", KompotProtocol.SCHEMA_DIALECT)
            put("\$id", KompotProtocol.ID_PREFIX + KompotProtocol.PROFILE_FILE_NAME)
            put("title", "KOMPOT profile")
            put("description", "The closed list of types supported by this build of the protocol")
            put("x-kompot-generated-by", "KompotSpec.profile — this file is generated, do not edit by hand")
            put("x-kompot-modules", JsonArray(schemas.map { JsonPrimitive(it.moduleName) }))
            putJsonObject("\$defs") {
                merged.forEach { (hierarchy, members) ->
                    val declared = extensions[hierarchy].orEmpty().sorted()
                    val extensionKey = "$hierarchy${KompotProtocol.EXTENSION_SUFFIX}"
                    val extensionRef = "#/\$defs/$extensionKey"

                    putJsonObject(hierarchy) {
                        put("x-kompot-kind", "hierarchy")
                        // Still false, and still not a contradiction with the module file that calls the
                        // same hierarchy open: the module states what the PROTOCOL permits, the profile
                        // states what THIS BUILD serves. §2.4 says which question each answers.
                        put("x-kompot-open", false)
                        // Copied from the owning module: what an unfamiliar type costs is a property of
                        // the hierarchy, and a reader validating against the profile alone has to be able
                        // to tell "one node becomes a placeholder" from "the whole response is lost".
                        degrades[hierarchy]?.let { put("x-kompot-degrades", it) }
                        if (declared.isNotEmpty()) {
                            put("x-kompot-extensions", JsonArray(declared.map { JsonPrimitive(it) }))
                        }
                        put(
                            "oneOf",
                            JsonArray(
                                members.values.map { target -> buildJsonObject { put("\$ref", target) } } +
                                    if (declared.isEmpty()) emptyList() else listOf(buildJsonObject { put("\$ref", extensionRef) }),
                            ),
                        )
                        putJsonObject("discriminator") {
                            put("propertyName", KompotProtocol.DISCRIMINATOR)
                            putJsonObject("mapping") {
                                members.forEach { (wireName, target) -> put(wireName, target) }
                                declared.forEach { wireName -> put(wireName, extensionRef) }
                            }
                        }
                    }

                    if (declared.isNotEmpty()) {
                        putJsonObject(extensionKey) {
                            put("x-kompot-kind", "extension")
                            put("description", extensionDescription(degrades[hierarchy]))
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject(KompotProtocol.DISCRIMINATOR) {
                                    put("enum", JsonArray(declared.map { JsonPrimitive(it) }))
                                }
                            }
                            put("required", JsonArray(listOf(JsonPrimitive(KompotProtocol.DISCRIMINATOR))))
                        }
                    }
                }
            }
        }
    }

    // The cost of an undeclared type is not the same in every hierarchy, so neither is the sentence.
    // Saying "safe, because it degrades" over a hierarchy that does not degrade is wrong in the one
    // direction that matters: it makes a reader relax.
    private fun extensionDescription(degrades: Boolean?): String {
        val common =
            "A wire type this deployment adds on top of its modules. Its NAME is declared, its shape is not: " +
                "there is no schema for it here. A type in neither the mapping above nor this list is a violation. "

        return common +
            when (degrades) {
                true ->
                    "Omitting the shape is safe here because this hierarchy degrades (x-kompot-degrades: true above): " +
                        "an implementation that knows nothing of the type draws a placeholder and keeps the screen"

                false ->
                    "This hierarchy does NOT degrade (x-kompot-degrades: false above): an implementation that meets " +
                        "the type without knowing it loses the WHOLE response, not one node. Roll out the readers " +
                        "before the writer starts sending it — the same rule SPEC.md §15 states for a type without " +
                        "degradation"

                null ->
                    "The owning module declares no x-kompot-degrades for this hierarchy, so what an unfamiliar type " +
                        "costs here is unstated — treat it as not degrading until it says otherwise"
            }
    }

    // ---- helpers for describing a module -----------------------------------------------------

    // `forbid` is the negative half of a format rule, and it exists because the positive half cannot
    // always carry it: a negative lookahead makes the pattern uncompilable for RE2 engines, and those
    // refuse the whole schema file rather than the one keyword. Expressed as `not`, the same rule
    // reaches every implementation (see KompotProtocol.DEEPLINK_FORBIDDEN_PATTERN).
    public fun constrained(
        pattern: String?,
        description: String,
        forbid: String? = null,
    ): JsonObject =
        buildJsonObject {
            if (pattern != null) put("pattern", pattern)
            if (forbid != null) {
                putJsonObject("not") { put("pattern", forbid) }
            }
            put("description", description)
        }

    public fun reservedMetadata(): JsonObject =
        constrained(
            pattern = null,
            description =
                "Arbitrary metadata available to the client locally. Two keys are reserved by the protocol: " +
                    "\"${KompotProtocol.METADATA_KEY_CURRENCY}\" is the currency amount_input.currencyFromField picks up, " +
                    "\"${KompotProtocol.METADATA_KEY_BALANCE}\" is the remaining amount max_amount_from_field reads. " +
                    "Every other key is a convention of the particular form",
        )

    public fun openHierarchy(
        description: String,
        degrades: Boolean,
        extraRequired: List<String> = emptyList(),
        extraProperties: JsonObject = JsonObject(emptyMap()),
    ): JsonObject =
        buildJsonObject {
            put("x-kompot-kind", "hierarchy")
            put("x-kompot-open", true)
            // Openness and degradation are different properties: all four form hierarchies can be
            // extended by plug-ins, but only KompotComponent and KompotAction survive an unknown type.
            put("x-kompot-degrades", degrades)
            put("description", description)
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject(KompotProtocol.DISCRIMINATOR) {
                    put("type", "string")
                    put("description", "The variant discriminator. The closed list of values is in the build's profile")
                }
                extraProperties.forEach { (key, value) -> put(key, value) }
            }
            put("required", JsonArray((listOf(KompotProtocol.DISCRIMINATOR) + extraRequired).map { JsonPrimitive(it) }))
            put("additionalProperties", true)
        }
}
