package io.github.youndie.kompot.studio.tree

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
internal fun RenderersMap.withSelectionBorder(selectedId: String?): RenderersMap =
    mapValues { (_, renderer) ->
        @Suppress("UNCHECKED_CAST") // the same unchecked cast the registry's own dispatch does
        SelectionBorderRenderer(renderer as KompotComponentRenderer<KompotComponent>, selectedId)
    }

private class SelectionBorderRenderer<T : KompotComponent>(
    private val delegate: KompotComponentRenderer<T>,
    private val selectedId: String?,
) : KompotComponentRenderer<T> {
    @Composable
    override fun Render(
        component: T,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        if (component.id != selectedId) {
            delegate.Render(component, actionHandler, formController)
            return
        }

        // propagateMinConstraints, and it is not decoration: a Box that does not propagate them hands
        // its child the minimum of nothing, so a node that filled its parent's width stops doing so
        // the moment it is selected — the screen would rearrange itself under the click that selected
        // it, and the frame would be around a differently shaped thing than the one being looked at.
        Box(
            modifier = Modifier.border(SELECTION_WIDTH, SELECTION_COLOUR),
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
internal const val SELECTION_RGB: Int = 0xFF3D00
internal val SELECTION_COLOUR: Color = Color(0xFF000000 or SELECTION_RGB.toLong())
private val SELECTION_WIDTH = 2.dp
