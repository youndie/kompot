package io.github.youndie.kompot

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
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
}
