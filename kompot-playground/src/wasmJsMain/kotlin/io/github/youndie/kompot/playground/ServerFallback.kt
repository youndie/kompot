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
internal fun String.withServerFallback(
    present: Boolean,
    type: String,
): String {
    val root = runCatching { editorJson.parseToJsonElement(this) }.getOrNull() ?: return this
    return editorJson.encodeToString(JsonElement.serializer(), root.mapNodes(if (present) type else null))
}

// Every node of the chosen type gets an equivalent, and every equivalent THIS PAGE added to a node of
// any other type is taken back — choosing another type moves the server's half with it. A fallback the
// body's author wrote is theirs and is left alone either way, recognised by not carrying our id.
private fun JsonElement.mapNodes(fallbackFor: String?): JsonElement =
    when (this) {
        is JsonObject -> {
            val mapped = entries.associate { (key, value) -> key to value.mapNodes(fallbackFor) }
            val id = (this[ID] as? JsonPrimitive)?.content
            val ours = id != null && ((this[FALLBACK] as? JsonObject)?.get(ID) as? JsonPrimitive)?.content == equivalentId(id)
            val wanted = id != null && (this[TYPE] as? JsonPrimitive)?.content == fallbackFor
            when {
                wanted && FALLBACK !in this -> JsonObject(mapped + (FALLBACK to equivalent(id)))
                ours && !wanted -> JsonObject(mapped - FALLBACK)
                else -> JsonObject(mapped)
            }
        }

        is JsonArray -> JsonArray(map { it.mapNodes(fallbackFor) })
        else -> this
    }

/**
 * The component types the body sends that today's client knows, in the order they first appear —
 * the words the switch can take away. Anything else (an action, a modifier, a type nobody knows) is
 * not a component a client could be older than.
 */
internal fun String.componentTypes(): List<String> = componentCounts().keys.toList()

/**
 * Which word the switch takes away after the body changed: the one chosen before if the body still
 * sends it; else the rarest type other than the root's — the root taken away leaves no screen to
 * compare, and the word a body uses once is the one it is showing off.
 */
internal fun String.chooseWord(previous: String?): String? {
    val counts = componentCounts()
    if (previous != null && previous in counts) return previous
    val root = counts.keys.firstOrNull()
    return counts.filterKeys { it != root }.minByOrNull { it.value }?.key ?: root
}

private fun String.componentCounts(): Map<String, Int> {
    val root = runCatching { editorJson.parseToJsonElement(this) }.getOrNull() ?: return emptyMap()
    val found = linkedMapOf<String, Int>()

    fun walk(element: JsonElement) {
        when (element) {
            is JsonObject -> {
                (element[TYPE] as? JsonPrimitive)?.content?.takeIf { it in TODAY_COMPONENTS }?.let { found[it] = (found[it] ?: 0) + 1 }
                element.values.forEach(::walk)
            }
            is JsonArray -> element.forEach(::walk)
            else -> Unit
        }
    }
    walk(root)
    return found
}

// An ordinary component, and that is the point: the equivalent a server names has to be something
// every client already draws, or it would need the same rollout as the type it stands in for. Its text
// does not name the type — the page takes any of them away.
private fun equivalent(id: String): JsonObject =
    JsonObject(
        mapOf(
            TYPE to JsonPrimitive("text"),
            ID to JsonPrimitive(equivalentId(id)),
            "text" to JsonPrimitive("The server named this text as the equivalent: this client is too old to draw what stood here."),
            "style" to JsonPrimitive("body_medium"),
            "color" to JsonPrimitive("on_surface_variant"),
        ),
    )

private fun equivalentId(id: String) = "$id-equivalent"

// The editor's own Json, and it has nothing to do with the two a client uses: this one only reshapes
// text a person reads, so it prints rather than decodes.
private val editorJson = Json { prettyPrint = true; prettyPrintIndent = "  " }

private const val TYPE = "type"
private const val ID = "id"
private const val FALLBACK = "fallback"
