package io.github.youndie.kompot

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.kompot.standard.KompotPageLoader
import io.github.youndie.kompot.standard.KompotPageResponse
import io.github.youndie.kompot.standard.LoadPageAction
import io.github.youndie.kompot.standard.PaginatedListComponent
import io.github.youndie.kompot.standard.TextComponent
import kotlin.test.Test

// The idiom of §16.4 is that an action answers with a navigate and the client re-opens the screen. On
// a screen holding a list that stopped working: the new tree arrived, the screen "reloaded", and the
// list went on showing what it was first given. On a board every card lives in a list, so nothing a
// person did to a card was ever visible.
//
// No server here on purpose — two trees under one id and a swap. The report narrowed it the long way
// round, through the transport and the cache, before suspecting the component.
@OptIn(ExperimentalTestApi::class)
class PaginatedListSwapTest {
    private fun listOf(text: String) =
        PaginatedListComponent(
            id = "swap-list",
            initialItems = listOf(TextComponent(id = "swap-item", text = text)),
        )

    @Test
    fun `a list given new items shows them`() =
        runDesktopComposeUiTest {
            var component by mutableStateOf(listOf("first"))
            setContent {
                TestKompotTheme {
                    CompositionLocalProvider(
                        LocalKompotRegistry provides KompotRegistry(kompotCoreRenderers + kompotStandardRenderers),
                        LocalKompotPageLoader provides NoPagesAtAll,
                    ) {
                        PaginatedListRenderer().Render(component, recordingActionHandler(), testFormController())
                    }
                }
            }

            onNodeWithText("first").assertIsDisplayed()

            component = listOf("second")
            waitForIdle()

            onNodeWithText("second").assertIsDisplayed()
        }

    // The regression this fix could plausibly cause, and the reason the effect is keyed on the
    // component rather than on its items: a recomposition that hands back an EQUAL tree must not undo
    // pages the person has already loaded.
    @Test
    fun `an unchanged tree does not throw away a page that was loaded`() =
        runDesktopComposeUiTest {
            val paged =
                PaginatedListComponent(
                    id = "feed",
                    initialItems = listOf(TextComponent(id = "one", text = "Page one")),
                    loadMoreAction = LoadPageAction(url = "/page2"),
                )
            var recompose by mutableStateOf(0)
            setContent {
                TestKompotTheme {
                    CompositionLocalProvider(
                        LocalKompotRegistry provides KompotRegistry(kompotCoreRenderers + kompotStandardRenderers),
                        LocalKompotPageLoader provides SecondPage,
                    ) {
                        @Suppress("UNUSED_EXPRESSION")
                        recompose
                        PaginatedListRenderer().Render(paged, recordingActionHandler(), testFormController())
                    }
                }
            }

            onNodeWithText("Show more").performClick()
            waitForIdle()
            onNodeWithText("Page two").assertIsDisplayed()

            // The same component instance again: nothing about the tree changed.
            recompose = 1
            waitForIdle()

            onNodeWithText("Page two").assertIsDisplayed()
        }
}

private object SecondPage : KompotPageLoader {
    override suspend fun loadPage(
        url: String,
        params: Map<String, String>,
    ): KompotPageResponse = KompotPageResponse(items = listOf(TextComponent(id = "two", text = "Page two")))
}
