package io.github.youndie.kompot.spec

import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

// One spec module == one schema file == one Gradle module. The split follows the repository's own
// modularity on purpose: a server implementation elsewhere must be able to say "I speak kompot-core
// and kompot-standard but not your catalogue plug-in" — the same "open core plus plug-ins" division
// the Kotlin modules have.
data class KompotSpecModule(
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
)

// Assembling a spec from an ordered list of modules. The list belongs to a PARTICULAR BUILD rather
// than to the toolkit: only an application knows the set of types that can really travel on its wire,
// and only it knows where its own modules sit among the toolkit's.
object KompotSpec {
    fun generateAll(modules: List<KompotSpecModule>): List<GeneratedSchema> {
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
    fun profile(schemas: List<GeneratedSchema>): JsonObject {
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

        return buildJsonObject {
            put("\$schema", KompotProtocol.SCHEMA_DIALECT)
            put("\$id", KompotProtocol.ID_PREFIX + KompotProtocol.PROFILE_FILE_NAME)
            put("title", "KOMPOT profile")
            put("description", "The closed list of types supported by this build of the protocol")
            put("x-kompot-generated-by", "KompotSpec.profile — this file is generated, do not edit by hand")
            put("x-kompot-modules", JsonArray(schemas.map { JsonPrimitive(it.moduleName) }))
            putJsonObject("\$defs") {
                merged.forEach { (hierarchy, members) ->
                    putJsonObject(hierarchy) {
                        put("x-kompot-kind", "hierarchy")
                        put("x-kompot-open", false)
                        put("oneOf", JsonArray(members.values.map { target -> buildJsonObject { put("\$ref", target) } }))
                        putJsonObject("discriminator") {
                            put("propertyName", KompotProtocol.DISCRIMINATOR)
                            putJsonObject("mapping") {
                                members.forEach { (wireName, target) -> put(wireName, target) }
                            }
                        }
                    }
                }
            }
        }
    }

    // ---- helpers for describing a module -----------------------------------------------------

    fun constrained(
        pattern: String?,
        description: String,
    ): JsonObject =
        buildJsonObject {
            if (pattern != null) put("pattern", pattern)
            put("description", description)
        }

    fun reservedMetadata(): JsonObject =
        constrained(
            pattern = null,
            description =
                "Arbitrary metadata available to the client locally. Two keys are reserved by the protocol: " +
                    "\"${KompotProtocol.METADATA_KEY_CURRENCY}\" is the currency amount_input.currencyFromField picks up, " +
                    "\"${KompotProtocol.METADATA_KEY_BALANCE}\" is the remaining amount max_amount_from_field reads. " +
                    "Every other key is a convention of the particular form",
        )

    fun openHierarchy(
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
