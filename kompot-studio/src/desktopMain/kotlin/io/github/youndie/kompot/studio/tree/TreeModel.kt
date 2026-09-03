package io.github.youndie.kompot.studio.tree

import io.github.youndie.kompot.spec.Slot
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// THE TREE IS BUILT FROM THE JSON, not from the decoded components, and that is the whole design.
//
// A type the client does not know decodes to UnknownComponent(originalType, fallback): the object in
// memory has lost the very thing the tree most needs to show. The body still has it. So the tree
// reads the body, and the schema only says where a component's children live — which is the other
// half of the same argument: the alternative is a hand-kept list of container types, and konekt's own
// note says five copies of that list existed and each went stale separately.
// `internal`, and deliberately so while nothing outside this module reads it: publishing the tree
// model would put kompot-spec's Slot in this artefact's public signature, and a type a consumer
// cannot name is worse than one that is not offered. B-16 and B-21 build on it from inside.
internal data class ScreenNode(
    // The validator's path notation, so a schema finding and a node line up with no translation.
    val path: String,
    val wireType: String,
    val id: String?,
    val label: String,
    // Whether the profile knows this type. False is not an error — an unfamiliar type degrades by
    // protocol — but it is exactly what somebody looking at a placeholder needs told.
    val known: Boolean,
    val children: List<ScreenNode>,
) {
    fun flatten(): List<ScreenNode> = listOf(this) + children.flatMap { it.flatten() }
}

// The three shapes a body arrives in, told apart by what they carry rather than by a flag — the same
// rule KompotPreview decodes by, and it has to be the same one: a tree that found its root somewhere
// else than the render did would be a picture of a different screen.
internal fun screenTree(
    body: JsonElement,
    slots: Map<String, List<Slot>>,
): ScreenNode? {
    val root = body as? JsonObject ?: return null

    return when {
        "schema" in root -> (root["screen"] as? JsonObject)?.let { node(it, "$.screen", slots) }
        "screen" in root -> (root["screen"] as? JsonObject)?.let { node(it, "$.screen", slots) }
        else -> node(root, "$", slots)
    }
}

private fun node(
    value: JsonObject,
    path: String,
    slots: Map<String, List<Slot>>,
): ScreenNode? {
    val wireType = (value["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
    val id = (value["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content

    // A type outside the profile has no declared slots, so its children — if it has any — are not
    // walked. That is honest rather than lazy: nothing in the body says which of its properties hold
    // components, and guessing would put a form field's rules in the screen tree.
    val declared = slots[wireType]

    val children =
        declared.orEmpty().flatMap { slot ->
            when (val held = value[slot.name]) {
                is JsonArray ->
                    held.mapIndexedNotNull { index, element ->
                        (element as? JsonObject)?.let { node(it, "$path.${slot.name}[$index]", slots) }
                    }

                is JsonObject -> listOfNotNull(node(held, "$path.${slot.name}", slots))
                else -> emptyList()
            }
        }

    return ScreenNode(
        path = path,
        wireType = wireType,
        id = id,
        label = labelFor(wireType, id, value),
        known = declared != null,
        children = children,
    )
}

// `type#id`, and the WORDS instead wherever a node carries any — text, a button's label, a read-only
// field's value. A screen is mostly words, and a tree of "text#a1b2, text#c3d4" tells a reader
// nothing they opened the panel to find out. The rule is "does this node carry text" rather than "is
// this node a text": it was written as the latter first, and the button that came out as `button#order`
// while its label sat right there in the object is what made the difference visible.
private fun labelFor(
    wireType: String,
    id: String?,
    value: JsonObject,
): String {
    val text = (value["text"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    if (!text.isNullOrBlank()) {
        val shortened = if (text.length > LABEL_LIMIT) text.take(LABEL_LIMIT).trimEnd() + "…" else text
        return "$wireType \"$shortened\""
    }
    return if (id == null) wireType else "$wireType#$id"
}

private const val LABEL_LIMIT = 32
