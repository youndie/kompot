package io.github.youndie.kompot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
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
