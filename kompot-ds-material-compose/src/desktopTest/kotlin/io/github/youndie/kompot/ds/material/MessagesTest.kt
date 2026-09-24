package io.github.youndie.kompot.ds.material

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotActionHandler
import io.github.youndie.kompot.commands.PerformAction
import io.github.youndie.kompot.kompotJson
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.ShowMessageAction
import io.github.youndie.kompot.withPerform
import kotlinx.serialization.PolymorphicSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// show_message (SPEC.md §16.4) drawn the Material way: a snackbar, and its button handing its own
// action to the same handler every other action goes through.
@OptIn(ExperimentalTestApi::class)
class MessagesTest {
    private val received = mutableListOf<KompotAction>()
    private val app = KompotActionHandler { received += it }

    // A screen with a snackbar host and one button that raises [raise] through the chain the
    // application would build — withSnackbarMessages inside withPerform.
    private fun screen(
        raise: KompotAction,
        perform: suspend (String) -> KompotAction = { error("no perform in this test") },
    ) = @androidx.compose.runtime.Composable {
        val host = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val handler = remember { app.withSnackbarMessages(host, scope).withPerform(scope) { url, _ -> perform(url) } }
        MaterialTheme {
            Column {
                Button(onClick = { handler.handle(raise) }) { Text("raise") }
                SnackbarHost(host)
            }
        }
    }

    @Test
    fun `a message is shown and still reaches the application`() =
        runDesktopComposeUiTest {
            setContent(screen(ShowMessageAction(text = "Saved")))
            onNodeWithText("raise").performClick()
            waitForIdle()
            onNodeWithText("Saved").assertIsDisplayed()
            assertEquals(listOf<KompotAction>(ShowMessageAction(text = "Saved")), received)
        }

    @Test
    fun `the message's button hands its action to the handler`() =
        runDesktopComposeUiTest {
            val undo = NavigateAction(deeplink = "app://board/undo")
            setContent(screen(ShowMessageAction(text = "Card moved", actionLabel = "Undo", action = undo)))
            onNodeWithText("raise").performClick()
            waitForIdle()
            onNodeWithText("Undo").performClick()
            waitForIdle()
            assertTrue(undo in received, "the undo reached the application: $received")
        }

    @Test
    fun `words without an action make no button`() =
        runDesktopComposeUiTest {
            setContent(screen(ShowMessageAction(text = "Copied", actionLabel = "Undo")))
            onNodeWithText("raise").performClick()
            waitForIdle()
            onNodeWithText("Copied").assertIsDisplayed()
            onNodeWithText("Undo").assertDoesNotExist()
        }

    // The path the item is about: an operation with no form around it answers with a message.
    @Test
    fun `a perform that answers with a message shows it`() =
        runDesktopComposeUiTest {
            setContent(screen(PerformAction(url = "/cards/7/archive", payload = emptyMap()), perform = { ShowMessageAction(text = "Archived") }))
            onNodeWithText("raise").performClick()
            waitForIdle()
            onNodeWithText("Archived").assertIsDisplayed()
        }

    @Test
    fun `the wire form decodes to the action`() {
        val decoded =
            kompotJson().decodeFromString(
                PolymorphicSerializer(KompotAction::class),
                """{"type":"show_message","text":"Saved","level":"error","actionLabel":"Retry","action":{"type":"navigate","deeplink":"app://retry"}}""",
            )
        assertEquals(
            ShowMessageAction(text = "Saved", level = "error", actionLabel = "Retry", action = NavigateAction(deeplink = "app://retry")),
            decoded,
        )
    }
}
