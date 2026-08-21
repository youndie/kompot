package io.github.youndie.kompot

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.kompot.standard.KompotPageLoader
import io.github.youndie.kompot.standard.KompotPageResponse
import io.github.youndie.kompot.standard.LoadPageAction
import io.github.youndie.kompot.standard.PaginatedListComponent
import io.github.youndie.kompot.standard.TextComponent
import kotlin.test.Test
import kotlin.test.assertEquals

private class FakePageLoader(
    private val pages: Map<String, KompotPageResponse>,
) : KompotPageLoader {
    val requestedUrls = mutableListOf<String>()

    override suspend fun loadPage(
        url: String,
        params: Map<String, String>,
    ): KompotPageResponse {
        requestedUrls += url
        return pages[url] ?: KompotPageResponse(items = emptyList())
    }
}

    // A registry holding only what these tests actually use: the text items of the list are drawn by
    // the ordinary text renderer.
private val testRegistry = KompotRegistry(kompotCoreRenderers + kompotStandardRenderers)

@OptIn(ExperimentalTestApi::class)
class PaginatedListRendererTest {
    @Test
    fun `renders initial items without any load-more button when there is no next page`() =
        runDesktopComposeUiTest {
            setContent {
                TestKompotTheme {
                    CompositionLocalProvider(
                        LocalKompotRegistry provides testRegistry,
                        LocalKompotPageLoader provides FakePageLoader(emptyMap()),
                    ) {
                        PaginatedListRenderer().Render(
                            component =
                                PaginatedListComponent(
                                    id = "list",
                                    initialItems =
                                        listOf(
                                            TextComponent(id = "item_1", text = "Order for Ada"),
                                            TextComponent(id = "item_2", text = "Internet bill"),
                                        ),
                                ),
                            actionHandler = recordingActionHandler(),
                            formController = testFormController(),
                        )
                    }
                }
            }

            onNodeWithText("Order for Ada").assertIsDisplayed()
            onNodeWithText("Internet bill").assertIsDisplayed()
            onAllNodesWithText("Show more").assertCountEquals(0)
        }

    @Test
    fun `renders emptyState when there are no items`() =
        runDesktopComposeUiTest {
            setContent {
                TestKompotTheme {
                    CompositionLocalProvider(
                        LocalKompotRegistry provides testRegistry,
                        LocalKompotPageLoader provides FakePageLoader(emptyMap()),
                    ) {
                        PaginatedListRenderer().Render(
                            component =
                                PaginatedListComponent(
                                    id = "list",
                                    initialItems = emptyList(),
                                    emptyState = TextComponent(id = "empty", text = "Nothing found"),
                                ),
                            actionHandler = recordingActionHandler(),
                            formController = testFormController(),
                        )
                    }
                }
            }

            onNodeWithText("Nothing found").assertIsDisplayed()
        }

    @Test
    fun `clicking load more appends the next page and hides the button once exhausted`() =
        runDesktopComposeUiTest {
            val loader =
                FakePageLoader(
                    mapOf(
                        "/v1/api/transactions?page=2" to
                            KompotPageResponse(
                                items = listOf(TextComponent(id = "item_3", text = "Payout")),
                                nextLoadAction = null,
                            ),
                    ),
                )

            setContent {
                TestKompotTheme {
                    CompositionLocalProvider(
                        LocalKompotRegistry provides testRegistry,
                        LocalKompotPageLoader provides loader,
                    ) {
                        PaginatedListRenderer().Render(
                            component =
                                PaginatedListComponent(
                                    id = "list",
                                    initialItems = listOf(TextComponent(id = "item_1", text = "Order for Ada")),
                                    loadMoreAction = LoadPageAction(url = "/v1/api/transactions?page=2"),
                                ),
                            actionHandler = recordingActionHandler(),
                            formController = testFormController(),
                        )
                    }
                }
            }

            onNodeWithText("Show more").performClick()
            waitForIdle()

            onNodeWithText("Payout").assertIsDisplayed()
            onAllNodesWithText("Show more").assertCountEquals(0)
            assertEquals(listOf("/v1/api/transactions?page=2"), loader.requestedUrls)
        }
}
