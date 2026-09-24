package io.github.youndie.kompot

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.standard.BoxComponent

internal fun boxAlignment(word: String?): Alignment =
    when (word) {
        "top_center" -> Alignment.TopCenter
        "top_end" -> Alignment.TopEnd
        "center_start" -> Alignment.CenterStart
        "center" -> Alignment.Center
        "center_end" -> Alignment.CenterEnd
        "bottom_start" -> Alignment.BottomStart
        "bottom_center" -> Alignment.BottomCenter
        "bottom_end" -> Alignment.BottomEnd
        else -> Alignment.TopStart
    }

// Layers (SPEC.md §4.8). Not Compose's Box, because of what `Fill` has to mean here. A Box measures
// every child against the constraints IT was given, so a layer that fills — the scrim under a caption,
// the frame a badge is aligned in — takes the whole window rather than the picture it lies on, and the
// box grows to the window with it. Compose answers that with matchParentSize, which exists only in
// BoxScope and only for both axes at once; the wire has a Size node per axis.
//
// So two passes. The children that do not fill set the box's extent, each axis on its own; then the
// children that fill are measured to exactly that extent on the axis they fill, and at most that on
// the other. A box whose every child fills an axis takes what it is offered there, like any Fill.
public class BoxRenderer : KompotComponentRenderer<BoxComponent> {
    @Composable
    override fun Render(
        component: BoxComponent,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        val registry = LocalKompotRegistry.current
        val alignment = boxAlignment(component.alignment)
        val fills = component.children.map { it.fillAxes() }

        Layout(
            modifier = component.modifiers.toComposeModifier(),
            content = {
                component.children.forEach { child ->
                    // One measurable per child whatever its renderer emits, and the minimum passed
                    // through, so a filling child receives the exact extent it is given.
                    Box(propagateMinConstraints = true) {
                        registry.RenderNode(child, actionHandler, formController)
                    }
                }
            },
        ) { measurables, constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val placeables = arrayOfNulls<Placeable>(measurables.size)

            var width = 0
            var height = 0
            var anyFixesWidth = false
            var anyFixesHeight = false
            measurables.forEachIndexed { i, measurable ->
                val (fillsWidth, fillsHeight) = fills[i]
                if (!fillsWidth && !fillsHeight) {
                    val placeable = measurable.measure(loose)
                    placeables[i] = placeable
                    width = maxOf(width, placeable.width)
                    height = maxOf(height, placeable.height)
                    anyFixesWidth = true
                    anyFixesHeight = true
                }
            }
            if (!anyFixesWidth) width = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
            if (!anyFixesHeight) height = if (constraints.hasBoundedHeight) constraints.maxHeight else 0
            width = width.coerceIn(constraints.minWidth, constraints.maxWidth)
            height = height.coerceIn(constraints.minHeight, constraints.maxHeight)

            measurables.forEachIndexed { i, measurable ->
                if (placeables[i] != null) return@forEachIndexed
                val (fillsWidth, fillsHeight) = fills[i]
                placeables[i] =
                    measurable.measure(
                        Constraints(
                            minWidth = if (fillsWidth) width else 0,
                            maxWidth = width,
                            minHeight = if (fillsHeight) height else 0,
                            maxHeight = height,
                        ),
                    )
            }

            layout(width, height) {
                placeables.forEach { placeable ->
                    placeable!!.place(
                        alignment.align(IntSize(placeable.width, placeable.height), IntSize(width, height), layoutDirection),
                    )
                }
            }
        }
    }
}

// Whether a node asks for the box's extent on each axis: a Size node saying Fill there, with no
// absolute number overriding it (the number wins, SPEC.md §5.4).
private fun KompotComponent.fillAxes(): Pair<Boolean, Boolean> {
    val size = modifiers.filterIsInstance<KompotModifierNode.Size>().lastOrNull() ?: return false to false
    return (size.width == SizeType.Fill && size.widthDp == null) to (size.height == SizeType.Fill && size.heightDp == null)
}
