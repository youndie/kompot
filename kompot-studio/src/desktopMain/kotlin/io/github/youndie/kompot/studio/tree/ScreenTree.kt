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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
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
    // What a drop on a node would do, for the row to draw before it happens. Answered by the window,
    // which has the profile's slots; the tree has only the nodes.
    dropKindOf: (ScreenNode) -> DropKind? = { null },
    // The node a drag is over, for whatever else wants to point at it — the preview draws a dashed
    // frame around the same container the tree tints.
    onHover: (ScreenNode?) -> Unit = {},
    empty: @Composable () -> Unit = { Text("The body carries no component tree.", Modifier.padding(12.dp), color = studioColors().dim) },
    onSelect: (ScreenNode) -> Unit,
) {
    if (root == null) {
        Box(modifier) { empty() }
        return
    }

    // Keyed by path, so a node stays open across an edit that did not move it — and the root is
    // open from the start, because a tree showing one closed line is a tree showing nothing.
    val open = remember { mutableStateMapOf(root.path to true) }
    val rows = remember(root, open.toMap()) { flatten(root, 0, open) }

    var hoveredPath by remember { mutableStateOf<String?>(null) }
    val hover =
        hoveredPath?.let { path ->
            rows.firstOrNull { it.node.path == path }?.let { row -> dropKindOf(row.node)?.let { Hover(path, it) } }
        }
    val current by rememberUpdatedState(onHover)
    LaunchedEffect(hover) { current(hover?.let { h -> rows.firstOrNull { it.node.path == h.path }?.node }) }

    // The row a move would take away, dimmed together with everything under it: it is the one place
    // the drag cannot land, and a row that looks like a target and refuses is a row that lies.
    val moving = DragSession.payload?.let(Dragged::path)

    LazyColumn(modifier) {
        items(rows, key = { it.node.path }) { row ->
            TreeRow(
                row = row,
                open = open,
                selected = row.node.path == selectedPath,
                mark = marks[row.node.path],
                hover = hover,
                dimmed = moving != null && row.node.path.isWithin(moving),
                onHover = { over -> hoveredPath = if (over) row.node.path else hoveredPath.takeIf { it != row.node.path } },
                onDrop = onDrop,
                onSelect = onSelect,
            )
        }
    }
}

private data class Hover(val path: String, val kind: DropKind)

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
    hover: Hover?,
    dimmed: Boolean,
    onHover: (Boolean) -> Unit,
    onDrop: (payload: String, targetPath: String) -> Unit,
    onSelect: (ScreenNode) -> Unit,
) {
    val node = row.node
    val colors = studioColors()
    val kind = hover?.kind?.takeIf { hover.path == node.path }
    // Inside the container the drag is over: the whole subtree is the target, and the tint says so
    // on every row of it rather than on the header alone.
    val insideTarget = hover?.kind == DropKind.INTO && node.path.isWithin(hover.path)
    val background =
        when {
            kind == DropKind.REPLACE -> colors.warning.copy(alpha = 0.14f)
            insideTarget -> colors.accent.copy(alpha = 0.14f)
            selected -> colors.selection
            else -> androidx.compose.ui.graphics.Color.Transparent
        }
    // Every row is both ends of a drag: a node can be picked up and a node can be dropped on. The
    // payload is the PATH rather than the node, so the drop is resolved against the body as it is
    // when the mouse comes up — a tree rebuilt mid-drag would otherwise hand over an index measured
    // on a screen that no longer exists. Both modifiers are remembered by path: a modifier rebuilt on
    // recomposition is a new modifier, and a gesture in flight does not survive its node changing.
    val target = rememberDropTarget(node.path, onHover) { payload -> onDrop(payload, node.path) }
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
            .background(background)
            .then(if (kind == DropKind.REPLACE) Modifier.border(1.dp, colors.warning) else Modifier)
            .then(if (kind == DropKind.AFTER) Modifier.insertionLine(colors.accent) else Modifier)
            .alpha(if (dimmed) 0.45f else 1f)
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
            // The slot a single-value child sits in, before its type: "action button" says which of
            // the banner's two children this is, and the type alone does not.
            slotOf(node.path)?.let { Text(it, color = colors.dim, fontSize = 12.sp, maxLines = 1) }
            Text(node.wireType, color = colors.text, maxLines = 1)
            Text(
                node.label.removePrefix(node.wireType).trimStart(),
                color = colors.dim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        when (kind) {
            DropKind.INTO -> Icon(StudioIcon.DROP_HERE, colors.accent)
            DropKind.REPLACE -> Icon(StudioIcon.SLOT_REPLACE, colors.warning)
            else -> {}
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

// The name of the slot a node is the single occupant of, or null for a member of a list — a list's
// members are told apart by order, and the slot name on every one of them would be noise.
private fun slotOf(path: String): String? {
    val last = path.substringAfterLast('.', "")
    return last.takeIf { it.isNotEmpty() && !it.contains('[') && !it.startsWith("$") }
}

// Where a drop after this row would put the node: a line along the row's bottom edge with a dot at
// its start, the way every editor draws an insertion point between two lines.
private fun Modifier.insertionLine(colour: androidx.compose.ui.graphics.Color): Modifier =
    drawBehind {
        val y = size.height - 1.dp.toPx()
        drawLine(colour, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
        drawCircle(colour, radius = 4.dp.toPx(), center = Offset(4.dp.toPx(), y))
    }
