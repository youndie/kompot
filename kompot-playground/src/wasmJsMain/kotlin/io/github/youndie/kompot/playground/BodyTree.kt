package io.github.youndie.kompot.playground

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// One node of the body, as the tree shows it.
internal data class BodyNode(
    val type: String,
    val id: String?,
    val words: String?,
    val depth: Int,
)

// THE WALK IS STRUCTURAL: a node is any object carrying a "type", and its children are whatever
// objects hang below it, directly or through arrays.
//
// Not schema-driven, and that is a decision rather than a shortcut. kompot-spec — where childSlots
// lives — is JVM-only by design, and it does not travel to a browser; but the deeper reason is the one
// the toolkit already gives for its own walk: a schema says what a child SLOT is, it does not say what
// EXISTS. A node of a type nobody described, a property a deployment added last week, a body somebody
// pasted in — a schema-driven walk goes quietly blind on each, and "quietly" is the problem on a page
// whose whole subject is the unfamiliar.
internal fun bodyNodes(body: String): List<BodyNode> {
    val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() ?: return emptyList()
    val nodes = mutableListOf<BodyNode>()
    collect(root, depth = 0, into = nodes)
    return nodes
}

private fun collect(
    element: JsonElement,
    depth: Int,
    into: MutableList<BodyNode>,
) {
    when (element) {
        is JsonObject -> {
            val type = (element["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val next = if (type == null) depth else depth + 1
            if (type != null) {
                into +=
                    BodyNode(
                        type = type,
                        id = (element["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content,
                        // The words the node carries, whatever property they live in: a label is what
                        // tells two buttons apart, and asking "is this a text node" would have missed
                        // the button that carries one (the same lesson the studio's tree learned).
                        words = WORD_KEYS.firstNotNullOfOrNull { key -> (element[key] as? JsonPrimitive)?.takeIf { it.isString }?.content },
                        depth = depth,
                    )
            }
            element.values.forEach { collect(it, next, into) }
        }

        is JsonArray -> element.forEach { collect(it, depth, into) }
        else -> Unit
    }
}

@Composable
internal fun BodyTree(
    body: String,
    unknownTypes: Set<String>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val nodes = remember(body) { bodyNodes(body) }

    Column(modifier.verticalScroll(rememberScrollState())) {
        nodes.forEach { node ->
            val unfamiliar = node.type in unknownTypes
            val selected = node.id != null && node.id == selectedId

            Text(
                text = label(node, unfamiliar),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color =
                    when {
                        unfamiliar -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                        // A second click on the selected node clears the selection: the outline in the
                        // render is a tool, and a tool you cannot put down is a mode.
                        .clickable { onSelect(node.id.takeIf { !selected }) }
                        .padding(start = (node.depth * 12).dp, top = 2.dp, bottom = 2.dp, end = 4.dp),
            )
        }
    }
}

// The node, in the reader's vocabulary: the wire type, the id that addresses it, and the first words
// it carries. The mark says the CLIENT did not understand this type — taken from what the sink
// reported rather than guessed from the profile, so the tree and the log cannot disagree.
private fun label(
    node: BodyNode,
    unfamiliar: Boolean,
): String {
    val head = node.type + (node.id?.let { "#$it" } ?: "")
    val tail = node.words?.let { "  \"" + it.take(28) + (if (it.length > 28) "…" else "") + "\"" }.orEmpty()
    return (if (unfamiliar) "!  " else "   ") + head + tail
}

private val WORD_KEYS = listOf("text", "title", "label")
