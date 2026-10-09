package io.github.youndie.kompot

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.KompotPageLoader
import io.github.youndie.kompot.standard.KompotPageResponse
import io.github.youndie.kompot.standard.TextComponent
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.Test
import kotlin.test.assertEquals

// Loading and error are the application's screens (SPEC.md §12.6): the loader draws the application's
// slots while the screen is on its way and when it did not arrive, and hands the error slot a retry.
@OptIn(ExperimentalTestApi::class)
class ScreenLoaderTest {
    private val registry = KompotRegistry(kompotCoreRenderers + kompotStandardRenderers)

    private fun screen(text: String) = TextComponent(id = "root", text = text)

    private val noPages =
        object : KompotPageLoader {
            override suspend fun loadPage(
                url: String,
                params: Map<String, String>,
            ): KompotPageResponse = error("no list on this screen")
        }

    @androidx.compose.runtime.Composable
    private fun loader(
        key: Any?,
        load: suspend () -> KompotComponent,
    ) = KompotScreenLoader(
        key = key,
        load = load,
        loading = { Text("the app's loading") },
        failed = { cause, retry -> Button(onClick = retry) { Text("the app's error: ${cause.message}") } },
    ) { tree -> registry.RenderNode(tree, recordingActionHandler(), testFormController()) }

    @Test
    fun `the application's loading frame is drawn until the screen arrives`() =
        runDesktopComposeUiTest {
            val arrival = CompletableDeferred<KompotComponent>()
            setContent { TestKompotTheme { loader("home") { arrival.await() } } }

            onNodeWithText("the app's loading").assertExists()
            runOnIdle { arrival.complete(screen("home screen")) }
            waitForIdle()
            onNodeWithText("home screen").assertExists()
            onNodeWithText("the app's loading").assertDoesNotExist()
        }

    @Test
    fun `a screen that did not arrive draws the application's error, and its retry loads again`() =
        runDesktopComposeUiTest {
            var calls = 0
            setContent {
                TestKompotTheme {
                    loader("home") {
                        calls++
                        if (calls == 1) error("no network") else screen("home screen")
                    }
                }
            }
            waitForIdle()
            onNodeWithText("the app's error: no network").assertExists()

            onNodeWithText("the app's error: no network").performClick()
            waitForIdle()
            onNodeWithText("home screen").assertExists()
            assertEquals(2, calls)
        }

    @Test
    fun `a new key loads the other screen`() =
        runDesktopComposeUiTest {
            var key by mutableStateOf("home")
            setContent { TestKompotTheme { loader(key) { screen("$key screen") } } }
            waitForIdle()
            onNodeWithText("home screen").assertExists()

            runOnIdle { key = "offer" }
            waitForIdle()
            onNodeWithText("offer screen").assertExists()
        }

    private fun board(page: String) =
        ColumnComponent(
            id = "board",
            children = (0 until 40).map { TextComponent(id = "row$it", text = "row $it of $page") },
        )

    /**
     * A new key of the same screen keeps the drawn tree, and the place in it, while it loads.
     *
     * The loader went back to its loading frame on every new key, and an application puts the whole
     * address in the key: every filter looked like a jump to another screen, the tree came down, the
     * person lost their scroll and watched a spinner where their list had been. The application says
     * the keys are one screen with screenKey — here the path, "board", under a changing query.
     */
    @Test
    fun `a new key keeps the drawn tree and its scroll while the next one is on its way`() =
        runDesktopComposeUiTest(width = 400, height = 300) {
            var key by mutableStateOf("page=1")
            val hanging = CompletableDeferred<KompotComponent>()
            setContent {
                TestKompotTheme {
                    CompositionLocalProvider(LocalKompotPageLoader provides noPages) {
                        KompotScreenLoader(
                            key = key,
                            screenKey = "board",
                            load = { if (key == "page=1") board("page 1") else hanging.await() },
                            loading = { Text("the app's loading") },
                            failed = { cause, _ -> Text("the app's error: ${cause.message}") },
                        ) { tree ->
                            KompotLazyScreen(
                                rootComponent = tree,
                                registry = registry,
                                formController = testFormController(),
                                actionHandler = recordingActionHandler(),
                                modifier = Modifier.fillMaxWidth().height(200.dp).testTag("screen"),
                            )
                        }
                    }
                }
            }
            waitForIdle()
            onNodeWithTag("screen").performScrollToIndex(30)
            waitForIdle()
            onNodeWithText("row 30 of page 1").assertIsDisplayed()

            runOnIdle { key = "page=2" }
            waitForIdle()
            onNodeWithText("the app's loading").assertDoesNotExist()
            onNodeWithText("row 30 of page 1").assertIsDisplayed()
            onNodeWithText("row 0 of page 1").assertDoesNotExist()

            runOnIdle { hanging.complete(board("page 2")) }
            waitForIdle()
            onNodeWithText("row 30 of page 2").assertIsDisplayed()
        }

    /**
     * Left at its default, every new key is another screen: nothing of the previous one is kept.
     *
     * A loader that serves different screens — one at the root, the whole address as its key — would
     * otherwise keep the previous screen drawn, and tappable, while the next one loads; keeping the
     * tree is something the application asks for with screenKey, not something it gets by upgrading.
     */
    @Test
    fun `by default a new key is another screen and draws the loading frame again`() =
        runDesktopComposeUiTest {
            var key by mutableStateOf("home")
            val hanging = CompletableDeferred<KompotComponent>()
            setContent {
                TestKompotTheme {
                    KompotScreenLoader(
                        key = key,
                        load = { if (key == "home") screen("home screen") else hanging.await() },
                        loading = { Text("the app's loading") },
                        failed = { cause, _ -> Text("the app's error: ${cause.message}") },
                    ) { tree -> registry.RenderNode(tree, recordingActionHandler(), testFormController()) }
                }
            }
            waitForIdle()
            onNodeWithText("home screen").assertExists()

            runOnIdle { key = "offer" }
            waitForIdle()
            onNodeWithText("the app's loading").assertExists()
            onNodeWithText("home screen").assertDoesNotExist()
        }

    /**
     * A load that fails over a drawn tree leaves the tree, and the error slot still gets the cause and
     * a retry — the application turns it into a notice, not a screen in place of the one it had.
     */
    @Test
    fun `a failed load keeps the drawn tree and hands the error slot its cause and a retry`() =
        runDesktopComposeUiTest {
            var key by mutableStateOf("page=1")
            var calls = 0
            val state = KompotScreenLoaderState()
            setContent {
                TestKompotTheme {
                    KompotScreenLoader(
                        key = key,
                        screenKey = "page",
                        state = state,
                        load = {
                            calls++
                            if (calls == 2) error("no network") else screen("$key screen")
                        },
                        loading = { Text("the app's loading") },
                        failed = { cause, retry ->
                            val over = if (state.screen != null) "over the tree" else "alone"
                            Button(onClick = retry) { Text("the app's error $over: ${cause.message}") }
                        },
                    ) { tree -> registry.RenderNode(tree, recordingActionHandler(), testFormController()) }
                }
            }
            waitForIdle()
            onNodeWithText("page=1 screen").assertExists()

            runOnIdle { key = "page=2" }
            waitForIdle()
            onNodeWithText("page=1 screen").assertExists()
            onNodeWithText("the app's error over the tree: no network").assertExists()
            assertEquals(false, state.isLoading)

            onNodeWithText("the app's error over the tree: no network").performClick()
            waitForIdle()
            onNodeWithText("page=2 screen").assertExists()
            onNodeWithText("the app's error over the tree: no network").assertDoesNotExist()
            assertEquals(3, calls)
        }

    // The application's own indicator over a kept tree reads this; without it, a filter that takes a
    // second looks exactly like a filter that did nothing.
    @Test
    fun `the state says a load is in flight while the tree stays drawn`() =
        runDesktopComposeUiTest {
            var key by mutableStateOf("page=1")
            val hanging = CompletableDeferred<KompotComponent>()
            val state = KompotScreenLoaderState()
            setContent {
                TestKompotTheme {
                    KompotScreenLoader(
                        key = key,
                        screenKey = "page",
                        state = state,
                        load = { if (key == "page=1") screen("page 1") else hanging.await() },
                        failed = { _, _ -> },
                    ) { tree -> registry.RenderNode(tree, recordingActionHandler(), testFormController()) }
                }
            }
            waitForIdle()
            assertEquals(false, state.isLoading, "the first load has arrived")

            runOnIdle { key = "page=2" }
            waitForIdle()
            assertEquals(true, state.isLoading)
            onNodeWithText("page 1").assertExists()

            runOnIdle { hanging.complete(screen("page 2")) }
            waitForIdle()
            assertEquals(false, state.isLoading)
            onNodeWithText("page 2").assertExists()
        }
}
