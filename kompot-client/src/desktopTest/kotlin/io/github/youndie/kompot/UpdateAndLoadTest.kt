package io.github.youndie.kompot

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.kompot.commands.LoadAction
import io.github.youndie.kompot.commands.PerformAction
import io.github.youndie.kompot.commands.UpdateAction
import io.github.youndie.kompot.commands.UpdateHistory
import io.github.youndie.kompot.realtime.UpdateComponentMessage
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.ExpandableComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.TextComponent
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// update and load (SPEC.md §16.4): an answer that replaces nodes by id, and a GET whose answer is an
// action. What the toolkit owes the application: the nodes change and nothing else does — no other node,
// no state under an id, no navigation — and the address the server names reaches the application, which
// keeps the history.
@OptIn(ExperimentalTestApi::class)
class UpdateAndLoadTest {
    private val registry = KompotRegistry(kompotCoreRenderers + kompotStandardRenderers)

    // What reached the application at the end of the chain, and the addresses it was handed.
    private val reached = mutableListOf<KompotAction>()
    private val addresses = mutableListOf<Pair<String, String>>()

    private val loadState = KompotLoadState()

    // The answers a test hands out, by url: a load waits until its answer is completed.
    private val answers = mutableMapOf<String, CompletableDeferred<KompotAction>>()
    private var performAnswer: KompotAction = UpdateAction(emptyList())

    private fun text(
        id: String,
        words: String = id,
    ) = TextComponent(id = id, text = words)

    private val catalog =
        ColumnComponent(
            id = "catalog",
            children =
                listOf(
                    text("title", "Catalog"),
                    ExpandableComponent(
                        id = "faq",
                        header = text("faq-header", "Delivery"),
                        content = text("faq-answer", "Two days"),
                    ),
                    text("results", "all brands"),
                    text("cart", "cart: 0"),
                ),
        )

    private fun DesktopComposeUiTest.screen(vararg presses: Pair<String, KompotAction>) {
        setContent {
            val overrides = remember { KompotNodeOverrides() }
            val scope = rememberCoroutineScope()
            val handler =
                remember {
                    KompotActionHandler { reached += it }
                        .withUpdates(overrides) { deeplink, history -> addresses += deeplink to history }
                        .withLoad(scope, loadState) { url -> answers.getValue(url).await() }
                        .withPerform(scope) { _, _ -> performAnswer }
                }
            TestKompotTheme {
                CompositionLocalProvider(LocalKompotNodeOverrides provides overrides) {
                    Column {
                        presses.forEach { (label, action) ->
                            Button(onClick = { handler.handle(action) }) { Text(label) }
                        }
                        KompotScreen(catalog, registry, testFormController(), handler)
                    }
                }
            }
        }
        waitForIdle()
    }

    @Test
    fun `a perform answered with an update replaces both nodes and touches nothing else`() =
        runDesktopComposeUiTest {
            performAnswer =
                UpdateAction(
                    listOf(
                        UpdateComponentMessage("results", text("results", "acme only")),
                        UpdateComponentMessage("cart", text("cart", "cart: 1")),
                    ),
                )
            screen("add" to PerformAction(url = "/cart/add"))
            // State under an id that the update does not name: an opened section stays open.
            onNodeWithText("Delivery").performClick()
            waitForIdle()
            onNodeWithText("Two days").assertExists()

            onNodeWithText("add").performClick()
            waitForIdle()

            onNodeWithText("acme only").assertExists()
            onNodeWithText("cart: 1").assertExists()
            onNodeWithText("all brands").assertDoesNotExist()
            onNodeWithText("Catalog").assertExists()
            onNodeWithText("Two days").assertExists()
            // No navigation: what reached the application is the press and the update, nothing else.
            assertEquals(listOf(PerformAction::class, UpdateAction::class), reached.map { it::class })
            assertEquals(emptyList(), addresses)
        }

    @Test
    fun `a load answered with an update and an address replaces the nodes and hands the address over`() =
        runDesktopComposeUiTest {
            answers["/ui/catalog/results?brand=acme"] = CompletableDeferred()
            screen("acme" to LoadAction("/ui/catalog/results?brand=acme"))

            onNodeWithText("acme").performClick()
            waitForIdle()
            // In flight: the application can draw its indicator, and the tree is still the old one.
            assertTrue(loadState.isLoading)
            onNodeWithText("all brands").assertExists()

            runOnIdle {
                answers.getValue("/ui/catalog/results?brand=acme").complete(
                    UpdateAction(
                        listOf(UpdateComponentMessage("results", text("results", "acme only"))),
                        deeplink = "app://catalog?brand=acme",
                    ),
                )
            }
            waitForIdle()

            onNodeWithText("acme only").assertExists()
            assertEquals(listOf("app://catalog?brand=acme" to UpdateHistory.PUSH), addresses)
            assertFalse(loadState.isLoading)
            assertTrue(reached.none { it is NavigateAction })
        }

    // Filters pressed one after another, and the network answering the first one last: the screen must
    // end on the second choice, which is the one the person made.
    @Test
    fun `of two loads the second answer stays on screen even when the first arrives later`() =
        runDesktopComposeUiTest {
            answers["/results?brand=acme"] = CompletableDeferred()
            answers["/results?brand=zeta"] = CompletableDeferred()
            screen("acme" to LoadAction("/results?brand=acme"), "zeta" to LoadAction("/results?brand=zeta"))

            onNodeWithText("acme").performClick()
            onNodeWithText("zeta").performClick()
            waitForIdle()

            runOnIdle {
                answers.getValue("/results?brand=zeta").complete(
                    UpdateAction(
                        listOf(UpdateComponentMessage("results", text("results", "zeta only"))),
                        deeplink = "app://catalog?brand=zeta",
                    ),
                )
            }
            waitForIdle()
            assertFalse(loadState.isLoading)
            runOnIdle {
                answers.getValue("/results?brand=acme").complete(
                    UpdateAction(
                        listOf(UpdateComponentMessage("results", text("results", "acme only"))),
                        deeplink = "app://catalog?brand=acme",
                    ),
                )
            }
            waitForIdle()

            onNodeWithText("zeta only").assertExists()
            onNodeWithText("acme only").assertDoesNotExist()
            assertEquals(listOf("app://catalog?brand=zeta" to UpdateHistory.PUSH), addresses)
        }

    @Test
    fun `a load answered with navigate is an ordinary navigation`() =
        runDesktopComposeUiTest {
            answers["/results?brand=gone"] = CompletableDeferred(NavigateAction(deeplink = "app://catalog"))
            screen("gone" to LoadAction("/results?brand=gone"))

            onNodeWithText("gone").performClick()
            waitForIdle()

            assertEquals(NavigateAction(deeplink = "app://catalog"), reached.last())
            assertEquals(emptyList(), addresses)
            assertFalse(loadState.isLoading)
        }

    @Test
    fun `an update names its history and an unfamiliar word is push`() =
        runDesktopComposeUiTest {
            val node = listOf(UpdateComponentMessage("title", text("title", "Catalog")))
            screen(
                "replace" to UpdateAction(node, deeplink = "app://a", history = "replace"),
                "sideways" to UpdateAction(node, deeplink = "app://b", history = "sideways"),
                "silent" to UpdateAction(node),
            )

            onNodeWithText("replace").performClick()
            onNodeWithText("sideways").performClick()
            onNodeWithText("silent").performClick()
            waitForIdle()

            assertEquals(listOf("app://a" to UpdateHistory.REPLACE, "app://b" to UpdateHistory.PUSH), addresses)
        }
}
