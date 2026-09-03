package io.github.youndie.kompot.studio.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// THE ICON SET, as path data on a 16-unit grid, stroked at 1.25 — the design's own SVGs, kept as the
// strings they were drawn as rather than redrawn by hand, so the window and the mockup stay one set.
// Monochrome and tinted at the call site, which is what lets one glyph serve both themes.
internal enum class StudioIcon(
    val strokes: List<String>,
    val fills: List<String> = emptyList(),
    val dashed: List<String> = emptyList(),
) {
    CHEVRON_DOWN(listOf("M4 6l4 4 4-4")),
    CHEVRON_RIGHT(listOf("M6 4l4 4-4 4")),

    // Tree: what a node is.
    COLUMN(listOf(RECT_11, "M2.5 6.5h11M2.5 9.5h11")),
    ROW(listOf(RECT_11, "M6.5 2.5v11M9.5 2.5v11")),
    SURFACE(listOf("M4.5 2.5h7a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2h-7a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2z"), fills = listOf("M6.5 5.5h3a1 1 0 0 1 1 1v3a1 1 0 0 1-1 1h-3a1 1 0 0 1-1-1v-3a1 1 0 0 1 1-1z")),
    TEXT(listOf("M3 4.5h10M8 4.5v8M6 12.5h4")),
    BUTTON(listOf("M4.5 5h7a3 3 0 0 1 0 6h-7a3 3 0 0 1 0-6z", "M5 8h6")),
    FIELD(listOf("M3 4.5h10a1.5 1.5 0 0 1 1.5 1.5v4a1.5 1.5 0 0 1-1.5 1.5H3A1.5 1.5 0 0 1 1.5 10V6A1.5 1.5 0 0 1 3 4.5z", "M4.5 6.5v3")),
    LIST(listOf("M6.5 4h7M6.5 8h7M6.5 12h7"), fills = listOf(dot(3.5f, 4f, 0.9f), dot(3.5f, 8f, 0.9f), dot(3.5f, 12f, 0.9f))),
    IMAGE(listOf(RECT_11, "M2.5 11l3-3 3 3 2-2 3 3"), fills = listOf(dot(6f, 6f, 1.25f))),
    UNKNOWN(listOf("M6.3 6.4a1.8 1.8 0 1 1 2.6 1.6c-.6.4-.9.8-.9 1.4"), fills = listOf(dot(8f, 11.4f, 0.6f)), dashed = listOf(RECT_11)),

    // Palette.
    MODULE(listOf("M2.5 5.5l5.5-3 5.5 3v5l-5.5 3-5.5-3zM2.5 5.5l5.5 3 5.5-3M8 8.5v5")),
    WITH_SAMPLE(listOf(circle(8f, 8f, 5.5f)), fills = listOf(dot(8f, 8f, 2.5f))),
    NO_SAMPLE(emptyList(), dashed = listOf(circle(8f, 8f, 5.5f))),

    // Actions.
    MOVE_UP(listOf("M8 13V3M4 7l4-4 4 4")),
    MOVE_DOWN(listOf("M8 3v10M4 9l4 4 4-4")),
    DUPLICATE(listOf("M7 5.5h5a1.5 1.5 0 0 1 1.5 1.5v5a1.5 1.5 0 0 1-1.5 1.5H7A1.5 1.5 0 0 1 5.5 12V7A1.5 1.5 0 0 1 7 5.5z", "M3.5 10.5v-7a1 1 0 0 1 1-1h7")),
    DELETE(listOf("M3 4.5h10M6.5 4.5V3h3v1.5M4.5 4.5l.6 8a1 1 0 0 0 1 .9h3.8a1 1 0 0 0 1-.9l.6-8")),
    SAVE(listOf("M3 3.5h8l2 2v7a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V4.5a1 1 0 0 1 1-1z", "M5 3.5V7h5V3.5M5.5 13.5V10h5v3.5")),
    CAPTURE(listOf("M3.5 5h9a1.5 1.5 0 0 1 1.5 1.5v5a1.5 1.5 0 0 1-1.5 1.5h-9A1.5 1.5 0 0 1 2 11.5v-5A1.5 1.5 0 0 1 3.5 5z", "M5.5 5l1-1.5h3l1 1.5", circle(8f, 9f, 2f))),
    COMPARE(listOf("M3.5 3.5h9a1.5 1.5 0 0 1 1.5 1.5v6a1.5 1.5 0 0 1-1.5 1.5h-9A1.5 1.5 0 0 1 2 11V5a1.5 1.5 0 0 1 1.5-1.5z", "M8 3.5v9M4.5 8h2M9.5 8h2")),
    KOTLIN(listOf("M3 3.5l5 4.5-5 4.5M9 12.5h4")),
    JSON(listOf("M6 3c-1.4 0-2 .7-2 2v1.4c0 .9-.5 1.4-1.4 1.6.9.2 1.4.7 1.4 1.6V11c0 1.3.6 2 2 2M10 3c1.4 0 2 .7 2 2v1.4c0 .9.5 1.4 1.4 1.6-.9.2-1.4.7-1.4 1.6V11c0 1.3-.6 2-2 2")),
    MINUS(listOf("M3.5 8h9")),
    ADD(listOf("M8 3.5v9M3.5 8h9")),
    REMOVE(listOf("M4.5 4.5l7 7M11.5 4.5l-7 7")),
    DRAG_HANDLE(emptyList(), fills = listOf(dot(6f, 4f, 1f), dot(10f, 4f, 1f), dot(6f, 8f, 1f), dot(10f, 8f, 1f), dot(6f, 12f, 1f), dot(10f, 12f, 1f))),
    SEARCH(listOf(circle(7f, 7f, 4f), "M10 10l3 3")),

    // Drawer and status.
    ERROR(listOf(circle(8f, 8f, 5.5f), "M6 6l4 4M10 6l-4 4")),
    WARNING(listOf("M8 2.5l6 11H2z", "M8 6.5v3.2"), fills = listOf(dot(8f, 11.6f, 0.6f))),
    OK(listOf(circle(8f, 8f, 5.5f), "M5.5 8.2l1.8 1.8 3.4-3.6")),
    INFO(listOf(circle(8f, 8f, 5.5f), "M8 7.5v3.5"), fills = listOf(dot(8f, 5.4f, 0.6f))),
    DRAFT(listOf("M8 5v3.5"), fills = listOf(dot(8f, 11f, 0.6f)), dashed = listOf(circle(8f, 8f, 5.5f))),
    SCREEN(listOf("M5.5 1.5h5a1.5 1.5 0 0 1 1.5 1.5v10a1.5 1.5 0 0 1-1.5 1.5h-5A1.5 1.5 0 0 1 4 13V3a1.5 1.5 0 0 1 1.5-1.5z", "M7 12.5h2")),
    STORY(listOf("M3 4h10M3 8h10M3 12h6"), fills = listOf("M11.5 10.5l2 1.5-2 1.5z")),
    DROP_HERE(listOf("M2.5 8h11M10 5l3.5 3-3.5 3"), dashed = listOf("M2.5 3.5v9")),
    NO_DROP(listOf(circle(8f, 8f, 5.5f), "M4.2 4.2l7.6 7.6")),
    SLOT_REPLACE(listOf("M8 6.5v3M6.5 8h3"), dashed = listOf("M4 4.5h8a1.5 1.5 0 0 1 1.5 1.5v4a1.5 1.5 0 0 1-1.5 1.5H4A1.5 1.5 0 0 1 2.5 10V6A1.5 1.5 0 0 1 4 4.5z")),
    COPY(listOf("M7 5.5h5a1.5 1.5 0 0 1 1.5 1.5v5a1.5 1.5 0 0 1-1.5 1.5H7A1.5 1.5 0 0 1 5.5 12V7A1.5 1.5 0 0 1 7 5.5z", "M3.5 10.5v-7a1 1 0 0 1 1-1h7")),
    NAVIGATE(listOf("M4.5 11.5l7-7M6 4.5h5.5V10")),
    OPEN_URL(listOf("M7 9l2-2M5.5 10.5l-1 1a1.8 1.8 0 0 1-2.5-2.5l2-2a1.8 1.8 0 0 1 2.5 0M10.5 5.5l1-1a1.8 1.8 0 0 1 2.5 2.5l-2 2a1.8 1.8 0 0 1-2.5 0")),
}

private const val RECT_11 = "M4 2.5h8a1.5 1.5 0 0 1 1.5 1.5v8a1.5 1.5 0 0 1-1.5 1.5H4A1.5 1.5 0 0 1 2.5 12V4A1.5 1.5 0 0 1 4 2.5z"

private fun circle(cx: Float, cy: Float, r: Float): String = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

private fun dot(cx: Float, cy: Float, r: Float): String = circle(cx, cy, r)

@Composable
internal fun Icon(
    icon: StudioIcon,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
) {
    val strokes = remember(icon) { icon.strokes.map { it.toPath() } }
    val fills = remember(icon) { icon.fills.map { it.toPath() } }
    val dashed = remember(icon) { icon.dashed.map { it.toPath() } }

    Canvas(modifier.size(size)) {
        val unit = this.size.width / GRID
        scale(unit, pivot = Offset.Zero) {
            val stroke = Stroke(width = STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round)
            strokes.forEach { drawPath(it, tint, style = stroke) }
            fills.forEach { drawPath(it, tint) }
            if (dashed.isNotEmpty()) {
                val dash = Stroke(width = STROKE, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 1.6f)))
                dashed.forEach { drawPath(it, tint, style = dash) }
            }
        }
    }
}

private fun String.toPath(): Path = PathParser().parsePathString(this).toPath()

private const val GRID = 16f
private const val STROKE = 1.25f
