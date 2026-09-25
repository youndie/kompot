package io.github.youndie.kompot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.standard.DividerComponent
import io.github.youndie.kompot.standard.SpacerComponent
import kotlin.math.roundToInt

// The words `alignment` and `arrangement` on row and column (SPEC.md §4.7), turned into what Compose
// lays out with. A word this client does not know means the default, as a colour token does: the
// stack is drawn, just not where the newer server wanted it.

internal fun columnAlignment(word: String?): Alignment.Horizontal =
    when (word) {
        "center" -> Alignment.CenterHorizontally
        "end" -> Alignment.End
        else -> Alignment.Start
    }

internal fun rowAlignment(word: String?): Alignment.Vertical =
    when (word) {
        "center" -> Alignment.CenterVertically
        "end" -> Alignment.Bottom
        else -> Alignment.Top
    }

// One arrangement for both axes, and written here rather than taken from Compose, because of what
// `spacing` means once there is free space to share. Compose's SpaceBetween and its siblings have no
// gap of their own, and its spacedBy has no free-space distribution; the protocol needs both at once.
// So: `spacing` is the SMALLEST gap between two children, always, and the arrangement distributes
// whatever is left over on top of it. A stack whose content does not fit gets exactly `spacing`,
// packed at the start — the same as having no arrangement at all.
internal class StackArrangement(
    private val word: String?,
    override val spacing: Dp,
) : Arrangement.HorizontalOrVertical {
    override fun Density.arrange(
        totalSize: Int,
        sizes: IntArray,
        layoutDirection: LayoutDirection,
        outPositions: IntArray,
    ) {
        place(totalSize, sizes, outPositions)
        // Right to left the first child starts at the right edge: mirror each position, the same
        // reading Compose gives its own arrangements.
        if (layoutDirection == LayoutDirection.Rtl) {
            for (i in sizes.indices) outPositions[i] = totalSize - outPositions[i] - sizes[i]
        }
    }

    override fun Density.arrange(
        totalSize: Int,
        sizes: IntArray,
        outPositions: IntArray,
    ) = place(totalSize, sizes, outPositions)

    private fun Density.place(
        totalSize: Int,
        sizes: IntArray,
        outPositions: IntArray,
    ) {
        if (sizes.isEmpty()) return
        val count = sizes.size
        val gap = spacing.toPx()
        val free = (totalSize - sizes.sum() - gap * (count - 1)).coerceAtLeast(0f)

        val (lead, extra) =
            when (word) {
                "center" -> free / 2 to 0f
                "end" -> free to 0f
                "space_between" -> 0f to (if (count > 1) free / (count - 1) else 0f)
                "space_around" -> (free / count) / 2 to free / count
                "space_evenly" -> free / (count + 1) to free / (count + 1)
                else -> 0f to 0f
            }

        var position = lead
        for (i in sizes.indices) {
            outPositions[i] = position.roundToInt()
            position += sizes[i] + gap + extra
        }
    }

    override fun toString(): String = "StackArrangement($word, $spacing)"
}

// Which way the stack a node sits in runs, for the two nodes whose meaning turns with it: a divider is
// a rule ACROSS the axis, a spacer is room ALONG it (SPEC.md §4.10). Provided by the row and column
// renderers; outside any stack — the root, a box — a node reads it as a column's.
public enum class KompotStackAxis { Vertical, Horizontal }

public val LocalKompotStackAxis: ProvidableCompositionLocal<KompotStackAxis> =
    compositionLocalOf { KompotStackAxis.Vertical }

public class DividerRenderer : KompotComponentRenderer<DividerComponent> {
    @Composable
    override fun Render(
        component: DividerComponent,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        // The table's rule when the server names nothing, so a divider and a table row line read as the
        // same line; a named token is resolved like any other.
        val color = component.color?.let { LocalKompotDesignSystem.current.resolveColor(it) } ?: MaterialTheme.colorScheme.outlineVariant
        val modifier = component.modifiers.toComposeModifier()
        when (LocalKompotStackAxis.current) {
            KompotStackAxis.Vertical -> HorizontalDivider(modifier = modifier, color = color)
            // Fills the row's height, which the row makes finite by measuring itself at its intrinsic
            // height whenever a divider is among its children (RowRenderer).
            KompotStackAxis.Horizontal -> VerticalDivider(modifier = modifier.fillMaxHeight(), color = color)
        }
    }
}

public class SpacerRenderer : KompotComponentRenderer<SpacerComponent> {
    @Composable
    override fun Render(
        component: SpacerComponent,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        val modifier = component.modifiers.toComposeModifier()
        when (LocalKompotStackAxis.current) {
            KompotStackAxis.Vertical -> Spacer(modifier.height(component.size.dp))
            KompotStackAxis.Horizontal -> Spacer(modifier.width(component.size.dp))
        }
    }
}
