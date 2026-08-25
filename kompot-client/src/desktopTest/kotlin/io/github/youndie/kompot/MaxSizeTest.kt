package io.github.youndie.kompot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.TextComponent
import kotlin.test.Test
import kotlin.test.assertTrue

// A reading measure: at most so many points wide, and on a narrower window as wide as the window.
// Neither an exact width nor a share of the parent can say that — one clips on a small window with no
// horizontal scroll to recover with, the other is never bounded at all.
@OptIn(ExperimentalTestApi::class)
class MaxSizeTest {
    // A real registry: the column renders its children through it, and with an empty one there is no
    // text on screen to measure at all.
    private val renderers = KompotRegistry(kompotCoreRenderers + kompotStandardRenderers)

    // Long enough that its natural width exceeds every window here: a short string is narrower than
    // the ceiling on its own, so measuring it would report a cap that never applied — and the control
    // below, which must find the text WIDER without a ceiling, would fail for the same reason.
    private val sentence =
        "A line of running text across a full window is not read at all, because the measure an eye " +
            "holds is some seventy characters and a wide window offers three times that, which is why " +
            "every reading surface needs a ceiling it can state rather than a width it must guess."

    private fun column(
        maxWidthDp: Int?,
        width: SizeType = SizeType.Fill,
    ) = ColumnComponent(
        id = "reading",
        modifiers = listOf(KompotModifierNode.Size(width = width, maxWidthDp = maxWidthDp)),
        children = listOf(TextComponent(id = "t", text = sentence)),
    )

    @Test
    fun `a filling column stops at its maximum on a window wider than it`() =
        runDesktopComposeUiTest(width = 1200, height = 300) {
            setContent {
                TestKompotTheme {
                    CompositionLocalProvider(LocalKompotRegistry provides renderers) {
                    Box(Modifier.size(1200.dp, 300.dp)) {
                        ColumnRenderer().Render(column(maxWidthDp = 400), recordingActionHandler(), testFormController())
                    }
                    }
                }
            }

            val width = onNodeWithText(sentence).getUnclippedBoundsInRoot().let { it.right - it.left }
            assertTrue(width <= 400.dp, "the text was laid out $width wide, past the 400.dp ceiling")
        }

    // The half a fixed width gets wrong: on a window narrower than the ceiling the column takes the
    // window, rather than being clipped at an edge there is no way to scroll past.
    @Test
    fun `on a window narrower than the maximum the column takes the window`() =
        runDesktopComposeUiTest(width = 300, height = 300) {
            setContent {
                TestKompotTheme {
                    CompositionLocalProvider(LocalKompotRegistry provides renderers) {
                    Box(Modifier.size(300.dp, 300.dp)) {
                        ColumnRenderer().Render(column(maxWidthDp = 400), recordingActionHandler(), testFormController())
                    }
                    }
                }
            }

            val width = onNodeWithText(sentence).getUnclippedBoundsInRoot().let { it.right - it.left }
            assertTrue(width <= 300.dp, "the text was laid out $width wide, past the window")
            assertTrue(width > 200.dp, "the text was laid out $width wide, which is not the window it was given")
        }

    // Without the ceiling the same column fills the window — the state the report describes, and the
    // control that says the test above measures the ceiling rather than something else.
    @Test
    fun `without a maximum the same column fills a wide window`() =
        runDesktopComposeUiTest(width = 1200, height = 300) {
            setContent {
                TestKompotTheme {
                    CompositionLocalProvider(LocalKompotRegistry provides renderers) {
                    Box(Modifier.size(1200.dp, 300.dp)) {
                        ColumnRenderer().Render(column(maxWidthDp = null), recordingActionHandler(), testFormController())
                    }
                    }
                }
            }

            assertTrue(onNodeWithText(sentence).getUnclippedBoundsInRoot().let { it.right - it.left } > 400.dp)
        }
}
