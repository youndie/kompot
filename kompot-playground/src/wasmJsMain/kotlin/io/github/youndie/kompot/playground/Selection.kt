package io.github.youndie.kompot.playground

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.youndie.kompot.KompotActionHandler
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotComponentRenderer
import io.github.youndie.kompot.KompotRegistry
import io.github.youndie.kompot.RenderersMap
import io.github.youndie.kompot.form.FormController

// SELECTING A NODE IN THE TREE OUTLINES IT IN THE RENDER, and the outline is put on by decorating the
// registry rather than by touching the body. Substituting the node — through the update channel, say —
// would change the very tree the page is showing.
//
// `KompotRegistry.decorated` is the seam the studio already uses for this: the map stays private, and
// what is added is the ability to WRAP a renderer, not to read the map.
internal fun KompotRegistry.outlining(selectedId: String?): KompotRegistry =
    if (selectedId == null) this else decorated { renderers -> renderers.mapValues { (_, renderer) -> Outlined(renderer, selectedId) } }

private class Outlined(
    private val delegate: KompotComponentRenderer<out KompotComponent>,
    private val selectedId: String,
) : KompotComponentRenderer<KompotComponent> {
    @Composable
    override fun Render(
        component: KompotComponent,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        // The registry keys renderers by the component's class, so the cast is guaranteed by the key
        // rather than by the type system — exactly as it is inside RenderNode itself.
        @Suppress("UNCHECKED_CAST")
        val renderer = delegate as KompotComponentRenderer<KompotComponent>

        if (component.id != selectedId) {
            renderer.Render(component, actionHandler, formController)
            return
        }

        // propagateMinConstraints, and it is not decoration: without it the selected node stops taking
        // its parent's width, and the screen re-lays-out under the very click that selected it.
        Box(
            Modifier.border(2.dp, MaterialTheme.colorScheme.primary),
            propagateMinConstraints = true,
        ) {
            renderer.Render(component, actionHandler, formController)
        }
    }
}
