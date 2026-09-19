package io.github.youndie.kompot.playground

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// THE SERVER'S HALF of the demonstration, done to the body rather than to the client.
//
// `fallback` is not something a client switch can turn on: only the server knows what a component it
// chose to send stands in for, which is the whole argument of SPEC.md §2.1 for putting the key on the
// wire. So selecting that state rewrites the BODY, visibly, in the editor — the page shows the key
// arriving instead of pretending the client found it somewhere.
//
// Written over the JSON tree rather than over the text: the body in the editor is whatever its reader
// typed, and a string replacement would work until the first time somebody reformatted it.
internal fun String.withServerFallback(present: Boolean): String {
    val root = runCatching { editorJson.parseToJsonElement(this) }.getOrNull() ?: return this
    return editorJson.encodeToString(JsonElement.serializer(), root.mapDemoNodes(present))
}

private fun JsonElement.mapDemoNodes(withFallback: Boolean): JsonElement =
    when (this) {
        is JsonObject -> {
            val mapped = entries.associate { (key, value) -> key to value.mapDemoNodes(withFallback) }
            val isDemoNode = (this[TYPE] as? JsonPrimitive)?.content == DEMO_TYPE
            when {
                !isDemoNode -> JsonObject(mapped)
                withFallback -> JsonObject(mapped + (FALLBACK to FALLBACK_COMPONENT))
                else -> JsonObject(mapped - FALLBACK)
            }
        }

        is JsonArray -> JsonArray(map { it.mapDemoNodes(withFallback) })
        else -> this
    }

// An ordinary component, and that is the point: the equivalent a server names has to be something
// every client already draws, or it would need the same rollout as the type it stands in for.
private val FALLBACK_COMPONENT: JsonObject =
    JsonObject(
        mapOf(
            TYPE to JsonPrimitive("text"),
            "id" to JsonPrimitive("promo_fallback"),
            "text" to JsonPrimitive("There is a promotion on. This client is too old to draw it, so the server said it in words."),
            "style" to JsonPrimitive("body_medium"),
            "color" to JsonPrimitive("on_surface_variant"),
        ),
    )

// The editor's own Json, and it has nothing to do with the two a client uses: this one only reshapes
// text a person reads, so it prints rather than decodes.
private val editorJson = Json { prettyPrint = true; prettyPrintIndent = "  " }

private const val TYPE = "type"
private const val FALLBACK = "fallback"
private const val DEMO_TYPE = "promo_banner"
