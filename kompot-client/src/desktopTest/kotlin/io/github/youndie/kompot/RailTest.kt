package io.github.youndie.kompot

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.youndie.kompot.standard.RowComponent
import io.github.youndie.kompot.standard.TextComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// A rail (SPEC.md §4.9): a row that keeps its width while its content scrolls sideways. Ten cards of
// 100 dp in a 300 dp row — the last one starts at 900, three widths past the edge, and the question
// is whether a gesture can bring it in, and whether the page around the rail still scrolls.
@OptIn(ExperimentalTestApi::class)
class RailTest {
    private val renderers = KompotRegistry(kompotCoreRenderers + kompotStandardRenderers)
    private val labels = ('a'..'j').map { it.toString() }

    private fun rail(scrollable: Boolean) =
        RowComponent(
            id = "rail",
            modifiers = listOf(KompotModifierNode.Size(widthDp = 300, heightDp = 60)),
            children =
                labels.map {
                    TextComponent(id = it, text = it, modifiers = listOf(KompotModifierNode.Size(widthDp = 100, heightDp = 60)))
                },
            scrollable = scrollable,
        )

    private fun layOut(
        content: @Composable () -> Unit,
        test: DesktopComposeUiTest.() -> Unit,
    ) = runDesktopComposeUiTest(width = 400, height = 400) {
        setContent {
            TestKompotTheme {
                CompositionLocalProvider(LocalKompotRegistry provides renderers) { content() }
            }
        }
        test()
    }

    @Composable
    private fun node(component: RowComponent) =
        Box(Modifier.testTag("host")) {
            renderers.RenderNode(component, recordingActionHandler(), testFormController())
        }

    private fun DesktopComposeUiTest.leftOf(label: String) = onNodeWithText(label).getUnclippedBoundsInRoot().left

    @Test
    fun `a swipe brings the far end of a scrollable row into view`() =
        layOut({ node(rail(scrollable = true)) }) {
            assertEquals(900.dp, leftOf("j"), "the last card starts three widths past the edge")
            onNodeWithTag("host").performTouchInput { swipeLeft() }
            waitForIdle()
            assertTrue(leftOf("j") < 900.dp, "a swipe moved the content (the last card is at ${leftOf("j")})")
        }

    // The control: the same swipe over the row the protocol always had moves nothing — so the test
    // above measures the field, not a harness in which any row slides.
    @Test
    fun `without the field the same swipe moves nothing`() =
        layOut({ node(rail(scrollable = false)) }) {
            // Not at 900: a row that does not scroll squeezes what does not fit to nothing at its edge,
            // which is exactly what a client older than the field shows (SPEC.md §4.9).
            val before = leftOf("j")
            assertEquals(300.dp, before, "the cards past the edge are squeezed to it")
            onNodeWithTag("host").performTouchInput { swipeLeft() }
            waitForIdle()
            assertEquals(before, leftOf("j"), "a row that does not scroll")
        }

    @Test
    fun `the vertical wheel over a rail still scrolls the page it is on`() {
        val page = ScrollState(0)
        layOut({
            Column(Modifier.size(360.dp, 400.dp).verticalScroll(page)) {
                Spacer(Modifier.height(100.dp))
                node(rail(scrollable = true))
                Spacer(Modifier.height(1200.dp))
            }
        }) {
            onNodeWithTag("host").performMouseInput {
                moveTo(center)
                repeat(10) { scroll(3f) }
            }
            mainClock.advanceTimeBy(2_000)
            waitForIdle()
            assertTrue(page.value > 200, "the page moved ${page.value} px under a wheel turned over the rail")
            assertEquals(900.dp, leftOf("j"), "the rail did not take the vertical wheel as its own")
        }
    }

    // What SPEC §4.9 promises about arrangement: while the content is narrower than the row there is
    // free space to share, so a short rail lays out like any row.
    @Test
    fun `a scrollable row shorter than its width still honours arrangement`() =
        layOut({
            node(
                RowComponent(
                    id = "rail",
                    modifiers = listOf(KompotModifierNode.Size(widthDp = 300, heightDp = 60)),
                    children = listOf("p", "q").map { TextComponent(id = it, text = it, modifiers = listOf(KompotModifierNode.Size(widthDp = 50, heightDp = 60))) },
                    arrangement = "end",
                    scrollable = true,
                ),
            )
        }) {
            assertEquals(300.dp, onNodeWithText("q").getUnclippedBoundsInRoot().right, "the last card at the far edge")
        }

    @Test
    fun `weight in a scrolling row is ignored rather than breaking the layout`() =
        layOut({
            node(
                RowComponent(
                    id = "rail",
                    modifiers = listOf(KompotModifierNode.Size(widthDp = 300, heightDp = 60)),
                    children =
                        listOf(
                            TextComponent(
                                id = "w",
                                text = "w",
                                modifiers = listOf(KompotModifierNode.Weight(1f), KompotModifierNode.Size(widthDp = 100, heightDp = 60)),
                            ),
                            TextComponent(id = "x", text = "x", modifiers = listOf(KompotModifierNode.Size(widthDp = 100, heightDp = 60))),
                        ),
                    scrollable = true,
                ),
            )
        }) {
            assertEquals(100.dp, leftOf("x"), "the weighted card keeps its own width")
        }
}
