package io.github.youndie.kompot

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.kompot.standard.KompotPageLoader
import io.github.youndie.kompot.standard.KompotPageResponse
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.LoadPageAction
import io.github.youndie.kompot.standard.PaginatedListComponent
import io.github.youndie.kompot.standard.TextComponent
import kotlin.test.Test
import kotlin.test.assertEquals

private class LazyScreenFakePageLoader(
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

private val lazyScreenTestRegistry = KompotRegistry(kompotCoreRenderers + kompotStandardRenderers)

// Covers KompotLazyScreen: a screen as a LazyColumn, where a paginated list among the root's direct
// children unfolds STRAIGHT INTO the parent LazyColumn rather than becoming a nested list. That is
// what gives real virtualisation for long lists, instead of a Column plus forEach — the path
// PaginatedListRendererTest covers, which remains in use for nested lists.
@OptIn(ExperimentalTestApi::class)
class KompotLazyScreenTest {
    @Test
    fun `renders regular children and flattened list items from a single LazyColumn`() =
        runDesktopComposeUiTest {
            setContent {
                TestKompotTheme {
                    CompositionLocalProvider(
                        LocalKompotRegistry provides lazyScreenTestRegistry,
                        LocalKompotPageLoader provides LazyScreenFakePageLoader(emptyMap()),
                    ) {
                        KompotLazyScreen(
                            rootComponent =
                                ColumnComponent(
                                    id = "root",
                                    children =
                                        listOf(
                                            TextComponent(id = "title", text = "Activity"),
                                            PaginatedListComponent(
                                                id = "list",
                                                initialItems =
                                                    listOf(
                                                        TextComponent(id = "item_1", text = "Order for Ada"),
                                                        TextComponent(id = "item_2", text = "Internet bill"),
                                                    ),
                                            ),
                                        ),
                                ),
                            registry = lazyScreenTestRegistry,
                            formController = testFormController(),
                            actionHandler = recordingActionHandler(),
                        )
                    }
                }
            }

            onNodeWithText("Activity").assertIsDisplayed()
            onNodeWithText("Order for Ada").assertIsDisplayed()
            onNodeWithText("Internet bill").assertIsDisplayed()
        }

    @Test
    fun `load more within a lazy screen appends items in place`() =
        runDesktopComposeUiTest {
            val loader =
                LazyScreenFakePageLoader(
                    mapOf(
                        "/v1/api/transactions?page=2" to
                            KompotPageResponse(items = listOf(TextComponent(id = "item_3", text = "Payout"))),
                    ),
                )

            setContent {
                TestKompotTheme {
                    CompositionLocalProvider(
                        LocalKompotRegistry provides lazyScreenTestRegistry,
                        LocalKompotPageLoader provides loader,
                    ) {
                        KompotLazyScreen(
                            rootComponent =
                                ColumnComponent(
                                    id = "root",
                                    children =
                                        listOf(
                                            PaginatedListComponent(
                                                id = "list",
                                                initialItems = listOf(TextComponent(id = "item_1", text = "Order for Ada")),
                                                loadMoreAction = LoadPageAction(url = "/v1/api/transactions?page=2"),
                                            ),
                                        ),
                                ),
                            registry = lazyScreenTestRegistry,
                            formController = testFormController(),
                            actionHandler = recordingActionHandler(),
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

        // A regression guard for a real bug: "the list did not refresh by itself, only after tapping
        // in and out of the search field". It reproduces the REAL path of a list screen.
    // (FormScreen: var state by remember{null}; LaunchedEffect(Unit){ state = loader() }) —
        // The culprit was not KompotLazyScreen, which redraws correctly on a new root component, but
        // the pattern "the screen leaves the composition and comes back". This confirms that a
        // paginated list with a fresh id on every fetch — what the DSL produces when no explicit id is
        // given — correctly resets remember(component.id) on a genuine remount, so new data shows
        // without touching the filters.
    @Test
    fun `remounting the screen (leave and re-enter) shows freshly fetched data without touching any filter field`() =
        runDesktopComposeUiTest {
            var fetchCount = 0
            val loader: suspend () -> KompotComponent = {
                fetchCount++
                ColumnComponent(
                    id = "root",
                    children =
                        listOf(
                                // Every fetch gets a new random id, exactly as a paginated list
                                // without an explicit id does — not one id reused.
                            PaginatedListComponent(
                                id = "list_fetch_$fetchCount",
                                initialItems = listOf(TextComponent(id = "item_$fetchCount", text = "Fetch #$fetchCount")),
                            ),
                        ),
                )
            }

            var mounted by mutableStateOf(true)

            setContent {
                TestKompotTheme {
                    CompositionLocalProvider(
                        LocalKompotRegistry provides lazyScreenTestRegistry,
                        LocalKompotPageLoader provides LazyScreenFakePageLoader(emptyMap()),
                    ) {
                        if (mounted) {
                            var state by remember { mutableStateOf<KompotComponent?>(null) }
                            LaunchedEffect(Unit) { state = loader() }
                            state?.let {
                                KompotLazyScreen(
                                    rootComponent = it,
                                    registry = lazyScreenTestRegistry,
                                    formController = testFormController(),
                                    actionHandler = recordingActionHandler(),
                                )
                            }
                        }
                    }
                }
            }

            waitForIdle()
            onNodeWithText("Fetch #1").assertIsDisplayed()

                // "leaving the screen" — the application switches to another one
            mounted = false
            waitForIdle()

                // "coming back" — a fresh LaunchedEffect(Unit) must call the loader again
            mounted = true
            waitForIdle()

            onNodeWithText("Fetch #2").assertIsDisplayed()
            onAllNodesWithText("Fetch #1").assertCountEquals(0)
            assertEquals(2, fetchCount)
        }

        // A live update of the list without a remount: unlike the test above, the id of the paginated
        // list does NOT change here, so remember(component.id) will not see the new initialItems by
        // itself — the list state has to pick them up from the live updates explicitly.
    @Test
    fun `a realtime update for the same paginated list id replaces its items without remounting`() =
        runDesktopComposeUiTest {
            var updates by mutableStateOf<Map<String, KompotComponent>>(emptyMap())

            setContent {
                TestKompotTheme {
                    CompositionLocalProvider(
                        LocalKompotRegistry provides lazyScreenTestRegistry,
                        LocalKompotPageLoader provides LazyScreenFakePageLoader(emptyMap()),
                        LocalKompotRealtimeUpdates provides updates,
                    ) {
                        KompotLazyScreen(
                            rootComponent =
                                ColumnComponent(
                                    id = "root",
                                    children =
                                        listOf(
                                            PaginatedListComponent(
                                                id = "transactions_list",
                                                initialItems = listOf(TextComponent(id = "item_1", text = "Order for Ada")),
                                            ),
                                        ),
                                ),
                            registry = lazyScreenTestRegistry,
                            formController = testFormController(),
                            actionHandler = recordingActionHandler(),
                        )
                    }
                }
            }

            onNodeWithText("Order for Ada").assertIsDisplayed()

                // "an update arrives from elsewhere" — the server sends a fresh list under THE SAME
                // id, the screen stays mounted, and nobody touches the search field.
            updates =
                mapOf(
                    "transactions_list" to
                        PaginatedListComponent(
                            id = "transactions_list",
                            initialItems =
                                listOf(
                                    TextComponent(id = "item_new", text = "Order from user2"),
                                    TextComponent(id = "item_1", text = "Order for Ada"),
                                ),
                        ),
                )
            waitForIdle()

            onNodeWithText("Order from user2").assertIsDisplayed()
            onNodeWithText("Order for Ada").assertIsDisplayed()
        }
}
