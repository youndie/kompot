package io.github.youndie.kompot.studio.tree

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.youndie.kompot.studio.diagnostics.Severity
import io.github.youndie.kompot.studio.ui.Icon
import io.github.youndie.kompot.studio.ui.StudioIcon
import io.github.youndie.kompot.studio.ui.studioColors
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
    selectedPath: String? = null,
    // The worst finding on each node, so a row can carry the mark the drawer would otherwise be the
    // only place to see: a tree that looks clean above a drawer full of errors is lying by omission.
    marks: Map<String, Severity> = emptyMap(),
    onDrop: (payload: String, targetPath: String) -> Unit = { _, _ -> },
    onSelect: (ScreenNode) -> Unit,
) {
    if (root == null) {
        Text("The body carries no component tree.", modifier.padding(12.dp), color = studioColors().dim)
        return
    }

    // Keyed by path, so a node stays open across an edit that did not move it — and the root is
    // open from the start, because a tree showing one closed line is a tree showing nothing.
    val open = remember { mutableStateMapOf(root.path to true) }
    val rows = remember(root, open.toMap()) { flatten(root, 0, open) }

    LazyColumn(modifier) {
        items(rows, key = { it.node.path }) { row ->
            TreeRow(row, open, row.node.path == selectedPath, marks[row.node.path], onDrop, onSelect)
        }
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
    selected: Boolean,
    mark: Severity?,
    onDrop: (payload: String, targetPath: String) -> Unit,
    onSelect: (ScreenNode) -> Unit,
) {
    val node = row.node
    val colors = studioColors()
    // Every row is both ends of a drag: a node can be picked up and a node can be dropped on. The
    // payload is the PATH rather than the node, so the drop is resolved against the body as it is
    // when the mouse comes up — a tree rebuilt mid-drag would otherwise hand over an index measured
    // on a screen that no longer exists. Both modifiers are remembered by path: a modifier rebuilt on
    // recomposition is a new modifier, and a gesture in flight does not survive its node changing.
    val target = rememberDropTarget(node.path) { payload -> onDrop(payload, node.path) }
    val drag = remember(node.path) { Modifier.dragPayload(Dragged.MOVE + node.path) }

    // NOT FOCUSABLE, and the reason is a crash rather than taste. A row that holds keyboard focus and
    // is then removed — which is what moving a node does to its old row — took the whole window down
    // inside Compose's accessibility sync (an NPE in ComposeSceneAccessibility when the focused node
    // vanished). Accessibility is switched on whenever an assistive client is attached, so this is
    // the ordinary case for anybody driving the window with one. Rows are clicked, never tabbed to.
    Row(
        Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .background(if (selected) colors.selection else androidx.compose.ui.graphics.Color.Transparent)
            .focusProperties { canFocus = false }
            .dropZone(target)
            .clickable { onSelect(node) }
            .padding(start = 8.dp + INDENT * row.depth, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
    ) {
        if (node.children.isNotEmpty()) {
            val expanded = open[node.path] == true
            Icon(
                if (expanded) StudioIcon.CHEVRON_DOWN else StudioIcon.CHEVRON_RIGHT,
                colors.dim,
                Modifier.focusProperties { canFocus = false }.clickable { open[node.path] = !expanded },
            )
        } else {
            Spacer(Modifier.width(16.dp))
        }

        // The glyph says what kind of node this is; a type outside the profile gets the one with the
        // question mark, in the warning colour, because that is the single most useful thing this
        // panel can say and it has to survive a screenshot and a colour-blind reader.
        Icon(iconFor(node), if (!node.known) colors.warning else if (selected) colors.text else colors.dim)

        // The label is what is picked up; the whole row is where things land. Kept apart because that
        // is the arrangement the move was verified on, not because the other was shown to fail.
        Row(drag.weight(1f), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
            Text(node.wireType, color = colors.text, maxLines = 1)
            Text(
                node.label.removePrefix(node.wireType).trimStart(),
                color = colors.dim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        when (mark) {
            Severity.ERROR -> Icon(StudioIcon.ERROR, colors.error)
            Severity.WARNING -> Icon(StudioIcon.WARNING, colors.warning)
            null -> {}
        }
    }
}

// Which glyph a wire type gets. Names rather than schema kinds, because the schema knows a slot from
// a property but not a button from a text; the rule falls back to the container glyph for anything
// with children and the field glyph for the form vocabulary's suffixes.
internal fun iconFor(node: ScreenNode): StudioIcon =
    when {
        !node.known -> StudioIcon.UNKNOWN
        node.wireType == "column" -> StudioIcon.COLUMN
        node.wireType == "row" -> StudioIcon.ROW
        node.wireType == "text" -> StudioIcon.TEXT
        node.wireType == "button" -> StudioIcon.BUTTON
        node.wireType == "image" -> StudioIcon.IMAGE
        node.wireType.endsWith("_list") || node.wireType == "list" -> StudioIcon.LIST
        node.wireType.endsWith("_input") || node.wireType.endsWith("_field") || node.wireType.endsWith("_group") -> StudioIcon.FIELD
        node.children.isNotEmpty() -> StudioIcon.SURFACE
        else -> StudioIcon.SURFACE
    }

private val ROW_HEIGHT = 24.dp
private val INDENT = 22.dp
