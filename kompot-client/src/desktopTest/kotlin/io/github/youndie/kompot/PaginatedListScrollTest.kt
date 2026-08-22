package io.github.youndie.kompot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.youndie.kompot.standard.KompotPageLoader
import io.github.youndie.kompot.standard.KompotPageResponse
import io.github.youndie.kompot.standard.PaginatedListComponent
import io.github.youndie.kompot.standard.TextComponent
import kotlin.test.Test

private object NoPages : KompotPageLoader {
    override suspend fun loadPage(
        url: String,
        params: Map<String, String>,
    ): KompotPageResponse = KompotPageResponse(items = emptyList())
}

// Paging is the whole point of the component, and it could not reach its own end: the renderer laid
// every item out in an ordinary column, so anything past the bottom of the box was clipped rather than
// scrolled to. A screen appeared to scroll only when its ROOT was a column — the lazy projection is
// used there — so a board rooted in a row scrolled nowhere at all, and the defect read as "some
// screens" rather than "lists".
@OptIn(ExperimentalTestApi::class)
class PaginatedListScrollTest {
    private val manyItems =
        PaginatedListComponent(
            id = "feed",
            initialItems = (1..30).map { TextComponent(id = "item_$it", text = "Entry $it") },
        )

    @Test
    fun `the last item of a bounded list can be reached`() =
        runDesktopComposeUiTest {
            setContent {
                TestKompotTheme {
                    CompositionLocalProvider(
                        LocalKompotRegistry provides KompotRegistry(kompotCoreRenderers + kompotStandardRenderers),
                        LocalKompotPageLoader provides NoPages,
                    ) {
                        // A bounded box is the case the report is about: a column beside a navigation
                        // rail, whose height is decided by the row around it.
                        Box(modifier = Modifier.height(200.dp)) {
                            PaginatedListRenderer().Render(manyItems, recordingActionHandler(), testFormController())
                        }
                    }
                }
            }

            // Both halves of the report's measurement: that a scroll action exists at all, and that
            // the end of the page can actually be arrived at. A lazy list does not compose what is not
            // visible, so scrolling TO the node is the only way to ask the second question.
            onNode(hasScrollAction()).performScrollToNode(hasText("Entry 30"))
            onNodeWithText("Entry 30").assertIsDisplayed()
        }
}
