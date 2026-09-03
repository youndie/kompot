package io.github.youndie.kompot.studio.tree

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import io.github.youndie.kompot.KompotActionHandler
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotComponentRenderer
import io.github.youndie.kompot.RenderersMap
import io.github.youndie.kompot.form.FormController

// Picking a node in the tree draws a frame around it in the render, and this is how.
//
// NOT through LocalKompotRealtimeUpdates, which is the hook that looks made for it: substituting a
// node by id replaces the component the render draws, so the thing highlighted would no longer be the
// thing the tree points at. Nor by wrapping the whole preview: knowing where a node sits needs
// something around that node.
//
// So the renderer map is decorated, exactly as withImpressionTracking decorates it — one wrapper
// around every renderer, drawing a border when the component's id is the selected one. The registry a
// consumer handed over is decorated rather than rebuilt (KompotRegistry.decorated), because a tool is
// given a finished registry and has no business asking for its parts.
//
// The same wrapper draws the DROP target while a drag is in the air — dashed, with "drop here" in
// the tag — so the container the tree tints and the container the preview frames are one node by
// construction.
internal fun RenderersMap.withSelectionBorder(
    selectedId: String?,
    dropId: String? = null,
): RenderersMap =
    mapValues { (_, renderer) ->
        @Suppress("UNCHECKED_CAST") // the same unchecked cast the registry's own dispatch does
        SelectionBorderRenderer(renderer as KompotComponentRenderer<KompotComponent>, selectedId, dropId)
    }

private class SelectionBorderRenderer<T : KompotComponent>(
    private val delegate: KompotComponentRenderer<T>,
    private val selectedId: String?,
    private val dropId: String?,
) : KompotComponentRenderer<T> {
    @Composable
    override fun Render(
        component: T,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        val dropping = dropId != null && component.id == dropId
        if (component.id != selectedId && !dropping) {
            delegate.Render(component, actionHandler, formController)
            return
        }

        // propagateMinConstraints, and it is not decoration: a Box that does not propagate them hands
        // its child the minimum of nothing, so a node that filled its parent's width stops doing so
        // the moment it is selected — the screen would rearrange itself under the click that selected
        // it, and the frame would be around a differently shaped thing than the one being looked at.
        // The id as a tag above the outline, in the outline's colour: which node is framed is the
        // question, and the frame alone answers it only when nothing else on the screen is that shape.
        val measurer = rememberTextMeasurer()
        val label = if (dropping) "${component.id} · drop here" else component.id
        Box(
            modifier =
                Modifier
                    .drawWithContent {
                        drawContent()
                        // Over the content, not under it: a frame is the one thing here that has to
                        // win against whatever the node paints.
                        drawRect(
                            SELECTION_COLOUR,
                            style = Stroke(SELECTION_WIDTH.toPx(), pathEffect = if (dropping) DASHED_EFFECT else null),
                        )
                        val layout = measurer.measure(label, TAG_STYLE)
                        val pad = 4.dp.toPx()
                        val height = layout.size.height + 2.dp.toPx()
                        drawRect(SELECTION_COLOUR, Offset(-1.dp.toPx(), -height), Size(layout.size.width + pad * 2, height))
                        drawText(layout, Color.White, Offset(-1.dp.toPx() + pad, -height + 1.dp.toPx()))
                    },
            propagateMinConstraints = true,
        ) {
            delegate.Render(component, actionHandler, formController)
        }
    }
}

// A colour that belongs to no palette in the composition: neither the toolkit's Material default nor
// any brand's, so a frame is never mistaken for something the screen itself drew. `internal` because
// the test reads it back out of a captured frame — a second copy of the number in the test would
// assert that the test and the test agree.
internal const val SELECTION_RGB: Int = 0x3574F0
internal val SELECTION_COLOUR: Color = Color(0xFF000000 or SELECTION_RGB.toLong())
private val SELECTION_WIDTH = 1.dp
private val DASHED_EFFECT = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))

private val TAG_STYLE = TextStyle(fontSize = 10.sp, fontFamily = FontFamily.Monospace)
