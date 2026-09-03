package io.github.youndie.kompot.studio.tree

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.Text

// The tree panel, as a flat list of indented rows this module lays out itself.
//
// It was Jewel's LazyTree first. It became this while a drag that would not start was being chased,
// and the chase ended somewhere else — the synthetic gesture driving the window released the mouse
// before AWT had opened a drag session, so a drag of sixty pixels ended before it entered anything
// and one of eighty pixels landed. Jewel was never shown to be at fault. The list stays because it
// is thirty lines that can be read, it is the version the drag was verified on, and it takes a
// pre-1.0 widget out of the one panel that has to be debugged from outside.
@Composable
internal fun ScreenTreePane(
    root: ScreenNode?,
    modifier: Modifier = Modifier,
    onDrop: (payload: String, targetPath: String) -> Unit = { _, _ -> },
    onSelect: (ScreenNode) -> Unit,
) {
    if (root == null) {
        Text("The body carries no component tree.", modifier.padding(8.dp))
        return
    }

    // Keyed by path, so a node stays open across an edit that did not move it — and the root is
    // open from the start, because a tree showing one closed line is a tree showing nothing.
    val open = remember { mutableStateMapOf(root.path to true) }
    val rows = remember(root, open.toMap()) { flatten(root, 0, open) }

    LazyColumn(modifier) {
        items(rows, key = { it.node.path }) { row -> TreeRow(row, open, onDrop, onSelect) }
    }
}

private class TreeRow(
    val node: ScreenNode,
    val depth: Int,
)

private fun flatten(
    node: ScreenNode,
    depth: Int,
    open: Map<String, Boolean>,
): List<TreeRow> =
    listOf(TreeRow(node, depth)) +
        if (open[node.path] == true) node.children.flatMap { flatten(it, depth + 1, open) } else emptyList()

@Composable
private fun TreeRow(
    row: TreeRow,
    open: MutableMap<String, Boolean>,
    onDrop: (payload: String, targetPath: String) -> Unit,
    onSelect: (ScreenNode) -> Unit,
) {
    val node = row.node
    // Every row is both ends of a drag: a node can be picked up and a node can be dropped on. The
    // payload is the PATH rather than the node, so the drop is resolved against the body as it is
    // when the mouse comes up — a tree rebuilt mid-drag would otherwise hand over an index measured
    // on a screen that no longer exists. Both modifiers are remembered by path: a modifier rebuilt on
    // recomposition is a new modifier, and a gesture in flight does not survive its node changing.
    val target = rememberDropTarget(node.path) { payload -> onDrop(payload, node.path) }
    val drag = remember(node.path) { Modifier.dragPayload(Dragged.MOVE + node.path) }

    // The label is what is picked up; the whole row is where things land. Kept apart because that
    // is the arrangement the move was verified on, not because the other was shown to fail.
    // NOT FOCUSABLE, and the reason is a crash rather than taste. A row that holds keyboard focus and
    // is then removed — which is what moving a node does to its old row — took the whole window down
    // inside Compose's accessibility sync (an NPE in ComposeSceneAccessibility when the focused node
    // vanished). Accessibility is switched on whenever an assistive client is attached, so this is
    // the ordinary case for anybody driving the window with one. Rows are clicked, never tabbed to.
    Row(
        Modifier
            .fillMaxWidth()
            .focusProperties { canFocus = false }
            .dropZone(target)
            .clickable { onSelect(node) }
            .padding(start = (row.depth * INDENT).dp, top = 3.dp, bottom = 3.dp, end = 8.dp),
    ) {
        if (node.children.isNotEmpty()) {
            val expanded = open[node.path] == true
            Text(
                if (expanded) "▾" else "▸",
                Modifier.width(16.dp).focusProperties { canFocus = false }.clickable { open[node.path] = !expanded },
            )
        } else {
            Text("", Modifier.width(16.dp))
        }
        // A marker rather than a colour: a type the profile does not carry is the single most useful
        // thing this panel can say, and it has to survive a screenshot and a colour-blind reader.
        // One line per node, however long an id is: a row that wraps is two rows to the eye, and the
        // eye counts rows to find a node.
        Text(
            if (node.known) node.label else "⚠ ${node.label}",
            drag.background(if (node.known) Color.Transparent else Color(0x22FF0000)),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val INDENT = 16
