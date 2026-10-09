package io.github.youndie.kompot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.youndie.kompot.realtime.KompotRealtimeSource
import io.github.youndie.kompot.realtime.UpdateComponentMessage
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.ExpandableComponent
import io.github.youndie.kompot.standard.KompotPageLoader
import io.github.youndie.kompot.standard.KompotPageResponse
import io.github.youndie.kompot.standard.RefreshAction
import io.github.youndie.kompot.standard.TabsComponent
import io.github.youndie.kompot.standard.TabsItem
import io.github.youndie.kompot.standard.TextComponent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A tree that arrives drops the screen's overrides even when it is equal to the drawn one (SPEC.md §4.4).
 *
 * The store was reset by a tree that DIFFERED from the drawn one: an equal tree was taken for "nothing
 * new". After an `update` that is exactly wrong — the overrides changed the screen and its address, the
 * person goes back, and the screen of the previous address is the very tree the overrides were written
 * over. It came, and the screen went on showing the filtered results under an address without the
 * filter. Equality decides only where nothing was delivered: a recomposition handing the screen an equal
 * tree again keeps them.
 */
@OptIn(ExperimentalTestApi::class)
class TreeArrivalTest {
    private val registry = KompotRegistry(kompotCoreRenderers + kompotStandardRenderers)

    private val noPages =
        object : KompotPageLoader {
            override suspend fun loadPage(
                url: String,
                params: Map<String, String>,
            ): KompotPageResponse = error("no list on this screen")
        }

    private fun page(text: String) =
        ColumnComponent(id = "page", children = listOf(TextComponent(id = "t", text = text)))

    private fun DesktopComposeUiTest.write(
        overrides: KompotNodeOverrides,
        component: KompotComponent,
    ) {
        runOnIdle { overrides.override(component.id, component) }
        waitForIdle()
    }

    /** The item's reproduction: `update` overrides a node, "back" hands the screen an equal tree. */
    @Test
    fun `an equal tree handed to the screen as a new arrival drops the overrides`() =
        runDesktopComposeUiTest {
            val overrides = KompotNodeOverrides()
            var tree by mutableStateOf<KompotComponent>(page("before"))
            var arrivals by mutableIntStateOf(0)
            setContent {
                TestKompotTheme {
                    CompositionLocalProvider(LocalKompotNodeOverrides provides overrides) {
                        KompotScreen(tree, registry, testFormController(), recordingActionHandler(), arrival = arrivals)
                    }
                }
            }
            waitForIdle()
            write(overrides, TextComponent(id = "t", text = "after"))
            onNodeWithText("after").assertExists()

            runOnIdle {
                tree = page("before")
                arrivals++
            }
            waitForIdle()
            onNodeWithText("before").assertExists()
            onNodeWithText("after").assertDoesNotExist()
        }

    /**
     * Nothing delivered, nothing dropped: the screen is composed again with a new instance of an equal
     * tree and the same arrival, and the override stays.
     *
     * The padding changes with the tree so that the screen itself runs again, not only its caller.
     * Below it the store is not asked at all: Compose compares the tree by value and skips the part that
     * takes the number, so only an arrival or a different tree reaches it.
     */
    @Test
    fun `a recomposition that hands the screen an equal tree keeps the overrides`() =
        runDesktopComposeUiTest {
            val overrides = KompotNodeOverrides()
            var tree by mutableStateOf<KompotComponent>(page("before"), neverEqualPolicy())
            var padding by mutableIntStateOf(0)
            var compositions = 0
            setContent {
                TestKompotTheme {
                    CompositionLocalProvider(
                        LocalKompotNodeOverrides provides overrides,
                        LocalKompotPageLoader provides noPages,
                    ) {
                        val drawn = tree
                        SideEffect { compositions++ }
                        KompotLazyScreen(
                            drawn,
                            registry,
                            testFormController(),
                            recordingActionHandler(),
                            contentPadding = PaddingValues(padding.dp),
                            arrival = 1,
                        )
                    }
                }
            }
            waitForIdle()
            write(overrides, TextComponent(id = "t", text = "after"))
            val before = compositions

            runOnIdle {
                tree = page("before")
                padding = 8
            }
            waitForIdle()
            assertTrue(compositions > before, "the control: the screen was composed again")
            onNodeWithText("after").assertExists()
            onNodeWithText("before").assertDoesNotExist()
        }

    @Test
    fun `a load that brings a tree equal to the drawn one drops the overrides`() =
        runDesktopComposeUiTest {
            val overrides = KompotNodeOverrides()
            var key by mutableStateOf("catalog?brand=acme")
            setContent {
                TestKompotTheme {
                    CompositionLocalProvider(LocalKompotNodeOverrides provides overrides) {
                        KompotScreenLoader(
                            key = key,
                            screenKey = "catalog",
                            load = { page("before") },
                            failed = { cause, _ -> Text("error: ${cause.message}") },
                        ) { tree -> KompotScreen(tree, registry, testFormController(), recordingActionHandler()) }
                    }
                }
            }
            waitForIdle()
            write(overrides, TextComponent(id = "t", text = "after"))
            onNodeWithText("after").assertExists()

            runOnIdle { key = "catalog" }
            waitForIdle()
            onNodeWithText("before").assertExists()
            onNodeWithText("after").assertDoesNotExist()
        }

    /** `refresh` under the loader: a counter in the key, the same screenKey, and the server's same tree. */
    @Test
    fun `a refresh through the loader that brings an equal tree drops the overrides`() =
        runDesktopComposeUiTest {
            val overrides = KompotNodeOverrides()
            var loads = 0
            setContent {
                var refreshes by remember { mutableIntStateOf(0) }
                val scope = rememberCoroutineScope()
                val handler = remember { KompotActionHandler {}.withRefresh(scope) { refreshes++ } }
                TestKompotTheme {
                    CompositionLocalProvider(LocalKompotNodeOverrides provides overrides) {
                        Column {
                            Button(onClick = { handler.handle(RefreshAction) }) { Text("refresh") }
                            KompotScreenLoader(
                                key = "board#$refreshes",
                                screenKey = "board",
                                load = {
                                    loads++
                                    page("card v1")
                                },
                                failed = { cause, _ -> Text("error: ${cause.message}") },
                            ) { tree -> KompotScreen(tree, registry, testFormController(), handler) }
                        }
                    }
                }
            }
            waitForIdle()
            write(overrides, TextComponent(id = "t", text = "card from a frame"))
            onNodeWithText("card from a frame").assertExists()

            onNodeWithText("refresh").performClick()
            waitForIdle()
            assertTrue(loads == 2, "the control: the refresh did load the screen again, $loads loads")
            onNodeWithText("card v1").assertExists()
            onNodeWithText("card from a frame").assertDoesNotExist()
        }

    /** `refresh` without the loader, on a lazy screen: the application bumps the arrival with the tree. */
    @Test
    fun `a refresh that hands a lazy screen an equal tree with a new arrival drops the overrides`() =
        runDesktopComposeUiTest {
            val overrides = KompotNodeOverrides()
            setContent {
                var tree by remember { mutableStateOf<KompotComponent>(page("card v1")) }
                var arrivals by remember { mutableIntStateOf(0) }
                val scope = rememberCoroutineScope()
                val handler =
                    remember {
                        KompotActionHandler {}.withRefresh(scope) {
                            tree = page("card v1")
                            arrivals++
                        }
                    }
                TestKompotTheme {
                    CompositionLocalProvider(
                        LocalKompotNodeOverrides provides overrides,
                        LocalKompotPageLoader provides noPages,
                    ) {
                        Column {
                            Button(onClick = { handler.handle(RefreshAction) }) { Text("refresh") }
                            KompotLazyScreen(tree, registry, testFormController(), handler, arrival = arrivals)
                        }
                    }
                }
            }
            waitForIdle()
            write(overrides, TextComponent(id = "t", text = "card from a frame"))
            onNodeWithText("card from a frame").assertExists()

            onNodeWithText("refresh").performClick()
            waitForIdle()
            onNodeWithText("card v1").assertExists()
            onNodeWithText("card from a frame").assertDoesNotExist()
        }

    /**
     * The arrival is the load that completed, not the one that started: a live frame stays over the tree
     * while the next key is on its way, goes when that key's tree arrives, and a frame written after the
     * arrival applies like any other.
     */
    @Test
    fun `a live frame stays while a load is on its way and one written after the arrival applies`() =
        runDesktopComposeUiTest {
            val frames = MutableSharedFlow<UpdateComponentMessage>(extraBufferCapacity = 8)
            val source = KompotRealtimeSource { frames }
            var key by mutableStateOf("catalog?brand=acme")
            val hanging = CompletableDeferred<KompotComponent>()
            setContent {
                TestKompotTheme {
                    KompotRealtimeProvider(topic = "catalog", source = source, content = {
                        KompotScreenLoader(
                            key = key,
                            screenKey = "catalog",
                            load = { if (key == "catalog?brand=acme") page("before") else hanging.await() },
                            failed = { cause, _ -> Text("error: ${cause.message}") },
                        ) { tree -> KompotScreen(tree, registry, testFormController(), recordingActionHandler()) }
                    })
                }
            }
            waitForIdle()
            runOnIdle { frames.tryEmit(UpdateComponentMessage("t", TextComponent(id = "t", text = "first frame"))) }
            waitForIdle()
            onNodeWithText("first frame").assertExists()

            runOnIdle { key = "catalog" }
            waitForIdle()
            onNodeWithText("first frame").assertExists()

            runOnIdle { hanging.complete(page("before")) }
            waitForIdle()
            onNodeWithText("before").assertExists()
            onNodeWithText("first frame").assertDoesNotExist()

            runOnIdle { frames.tryEmit(UpdateComponentMessage("t", TextComponent(id = "t", text = "second frame"))) }
            waitForIdle()
            onNodeWithText("second frame").assertExists()
        }

    private fun settings() =
        ColumnComponent(
            id = "page",
            children =
                listOf(
                    TextComponent(id = "t", text = "card v1"),
                    TabsComponent(
                        id = "settings",
                        tabs =
                            listOf(
                                TabsItem("Profile", TextComponent(id = "profile", text = "profile pane")),
                                TabsItem("Security", TextComponent(id = "security", text = "security pane")),
                            ),
                    ),
                    ExpandableComponent(
                        id = "faq",
                        header = TextComponent(id = "faq-header", text = "What is kompot?"),
                        content = TextComponent(id = "faq-answer", text = "the answer"),
                    ),
                ),
        )

    /** Only the overrides go: the tab the person chose and the section they opened stay with their ids. */
    @Test
    fun `an equal tree that arrives leaves the state under ids alone`() =
        runDesktopComposeUiTest {
            val overrides = KompotNodeOverrides()
            var key by mutableStateOf("settings#0")
            setContent {
                TestKompotTheme {
                    CompositionLocalProvider(LocalKompotNodeOverrides provides overrides) {
                        KompotScreenLoader(
                            key = key,
                            screenKey = "settings",
                            load = { settings() },
                            failed = { cause, _ -> Text("error: ${cause.message}") },
                        ) { tree -> KompotScreen(tree, registry, testFormController(), recordingActionHandler()) }
                    }
                }
            }
            waitForIdle()
            onNodeWithText("Security").performClick()
            onNodeWithText("What is kompot?").performClick()
            waitForIdle()
            write(overrides, TextComponent(id = "t", text = "card from a frame"))
            onNodeWithText("card from a frame").assertExists()

            runOnIdle { key = "settings#1" }
            waitForIdle()
            onNodeWithText("card v1").assertExists()
            onNodeWithText("card from a frame").assertDoesNotExist()
            onNodeWithText("security pane").assertExists()
            onNodeWithText("profile pane").assertDoesNotExist()
            onNodeWithText("the answer").assertExists()
        }
}
