package io.github.youndie.kompot.studio.palette

import io.github.youndie.kompot.encodeKompotComponent
import io.github.youndie.kompot.spec.KompotProtocol
import io.github.youndie.kompot.studio.KompotStudioConfig
import io.github.youndie.kompot.studio.inspector.defKeyFor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

// WHAT CAN BE ADDED TO A SCREEN, which is the closed list the profile already carries — grouped by the
// module each type came from, because that grouping is the one a deployment already thinks in: the
// toolkit's standard set, its forms, and its own components beside them.
internal data class PaletteEntry(
    val wireType: String,
    val group: String,
    val hasSample: Boolean,
)

internal fun paletteFor(config: KompotStudioConfig): List<PaletteEntry> {
    val profile = config.schemas[KompotProtocol.PROFILE_FILE_NAME] ?: return emptyList()
    val base =
        (profile["\$defs"] as? JsonObject)?.get(KompotProtocol.COMPONENT_HIERARCHY)?.jsonObject
            ?: return emptyList()
    val mapping = (base["discriminator"] as? JsonObject)?.get("mapping")?.jsonObject ?: return emptyList()
    val samples = config.samples.map { it.first }.toSet()

    return mapping.entries
        .map { (wireType, reference) ->
            // The schema file a type is defined in IS its module: the generator writes one file per
            // spec module, so the grouping needs no second list to go stale.
            val file = (reference as JsonPrimitive).content.substringBefore('#')
            PaletteEntry(
                wireType = wireType,
                group = file.removeSuffix(".schema.json"),
                hasSample = wireType in samples,
            )
        }.sortedWith(compareBy({ it.group }, { it.wireType }))
}

// The node a palette entry drops in.
//
// A deployment's own sample first — one fully filled instance is what a dictionary is for, and a node
// that arrives filled is one somebody can see. Failing that, the required properties and nothing else:
// a `text` with no words is still a text, and the fields it cannot invent are exactly what the schema
// layer will complain about a moment later. That complaint is the point rather than a gap — a builder
// that quietly invented a deeplink would be worse than one that says the deeplink is missing.
internal fun newNode(
    config: KompotStudioConfig,
    wireType: String,
    id: String,
): String {
    config.samples.firstOrNull { it.first == wireType }?.let { (_, sample) ->
        return config.json.encodeKompotComponent(sample)
    }

    val definition = definitionOf(config, wireType)
    val properties = definition?.get("properties") as? JsonObject
    val required =
        (definition?.get("required") as? JsonArray).orEmpty().map { (it as JsonPrimitive).content }

    val written =
        required
            .filter { it != KompotProtocol.DISCRIMINATOR && it != "id" }
            .mapNotNull { name ->
                val schema = properties?.get(name) as? JsonObject ?: return@mapNotNull null
                minimalValue(schema)?.let { "\"$name\": $it" }
            }

    return (listOf("\"type\": \"$wireType\"", "\"id\": \"$id\"") + written).joinToString(", ", "{ ", " }")
}

// The emptiest legal value of a shape, and nothing cleverer: a string is empty, a list is empty, a
// number is zero. Anything referring to another definition gets nothing at all — a nested object
// invented here would be a guess wearing the shape of data.
private fun minimalValue(schema: JsonObject): String? {
    val unwrapped =
        (schema["anyOf"] as? JsonArray ?: schema["oneOf"] as? JsonArray)
            ?.map { it.jsonObject }
            ?.firstOrNull { (it["type"] as? JsonPrimitive)?.content != "null" }
            ?: schema

    if (unwrapped["\$ref"] != null) return null

    return when (val type = (unwrapped["type"] as? JsonPrimitive)?.content) {
        "string" -> "\"\""
        "integer", "number" -> "0"
        "boolean" -> "false"
        "array" -> "[]"
        else -> if (type == "object") "{}" else null
    }
}

internal fun definitionOf(
    config: KompotStudioConfig,
    wireType: String,
): JsonObject? {
    val key = defKeyFor(config.schemas, wireType) ?: return null
    return config.schemas.values.firstNotNullOfOrNull { document ->
        (document["\$defs"] as? JsonObject)?.get(key)?.jsonObject
    }
}

private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()
