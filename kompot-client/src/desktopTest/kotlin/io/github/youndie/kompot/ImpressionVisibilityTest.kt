package io.github.youndie.kompot

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.youndie.kompot.analytics.AnalyticsEvent
import io.github.youndie.kompot.analytics.AnalyticsTracker
import io.github.youndie.kompot.analytics.KompotEventNamingRegistry
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.TextComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals

// An impression is the node being SEEN (B-54): a card 1000 dp down a 300 dp window is not counted when
// the screen opens, only once it is scrolled to. Only the card is tracked here, so the numbers are the
// card's own.
@OptIn(ExperimentalTestApi::class)
class ImpressionVisibilityTest {
    private val recorded = mutableListOf<String>()
    private val tracker = AnalyticsTracker { if (it is AnalyticsEvent.ComponentImpression) recorded += it.descriptor.properties["componentId"].toString() }
    private val page = ScrollState(0)
    private lateinit var scope: CoroutineScope

    private val tree =
        ColumnComponent(
            id = "root",
            children =
                listOf(
                    TextComponent(id = "top", text = "top", modifiers = listOf(KompotModifierNode.Size(heightDp = 1000))),
                    TextComponent(id = "card", text = "card", modifiers = listOf(KompotModifierNode.Size(heightDp = 100))),
                ),
        )

    private fun DesktopComposeUiTest.screen(visibility: ImpressionVisibility) {
        val registry =
            KompotRegistry((kompotCoreRenderers + kompotStandardRenderers).withImpressionTracking(tracker, KompotEventNamingRegistry(), visibility))
        setContent {
            scope = rememberCoroutineScope()
            TestKompotTheme {
                CompositionLocalProvider(LocalKompotRegistry provides registry) {
                    Column(Modifier.width(300.dp).height(300.dp).verticalScroll(page)) {
                        registry.RenderNode(tree, recordingActionHandler(), testFormController())
                    }
                }
            }
        }
    }

    private fun DesktopComposeUiTest.scrollTo(px: Int) {
        runOnIdle { scope.launch { page.scrollTo(px) } }
        waitForIdle()
    }

    @Test
    fun `a card below the window is counted when it is scrolled to, not when the screen opens`() =
        runDesktopComposeUiTest(width = 300, height = 300) {
            screen(ImpressionVisibility(track = { it.id == "card" }))
            waitForIdle()
            assertEquals(emptyList(), recorded, "not seen yet")

            scrollTo(page.maxValue)
            assertEquals(listOf("card"), recorded)
        }

    @Test
    fun `a card scrolled past faster than the minimum time is not counted`() =
        runDesktopComposeUiTest(width = 300, height = 300) {
            mainClock.autoAdvance = false
            screen(ImpressionVisibility(minVisibleMillis = 1_000, track = { it.id == "card" }))
            mainClock.advanceTimeBy(100)

            scrollTo(page.maxValue)
            mainClock.advanceTimeBy(500)
            scrollTo(0)
            mainClock.advanceTimeBy(2_000)
            assertEquals(emptyList(), recorded, "half a second on screen is not an impression")

            scrollTo(page.maxValue)
            mainClock.advanceTimeBy(1_500)
            assertEquals(listOf("card"), recorded, "a second and a half is")
        }

    // Without a predicate every node counts, and a node taller than the window counts once it fills it:
    // the root column is 1100 dp in a 300 dp window, never half inside, and still plainly seen.
    @Test
    fun `a node larger than the window counts when it fills the window`() =
        runDesktopComposeUiTest(width = 300, height = 300) {
            screen(ImpressionVisibility())
            waitForIdle()
            assertEquals(setOf("root", "top"), recorded.toSet(), "the card is below the window")

            scrollTo(page.maxValue)
            assertEquals(setOf("root", "top", "card"), recorded.toSet())
            assertEquals(3, recorded.size, "each once")
        }
}
