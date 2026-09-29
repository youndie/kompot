package io.github.youndie.kompot.ds.material

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotActionHandler
import io.github.youndie.kompot.KompotRegistry
import io.github.youndie.kompot.LocalKompotDesignSystem
import io.github.youndie.kompot.LocalKompotRegistry
import io.github.youndie.kompot.commands.PerformAction
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.form.FormSchema
import io.github.youndie.kompot.kompotCoreRenderers
import io.github.youndie.kompot.kompotStandardRenderers
import io.github.youndie.kompot.standard.ButtonComponent
import io.github.youndie.kompot.standard.CloseAction
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.ConfirmAction
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.PresentAction
import io.github.youndie.kompot.standard.TextComponent
import io.github.youndie.kompot.withPerform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// present and confirm (SPEC.md §12.5), drawn the Material way. The chain is the one an application
// builds: withOverlays inside withPerform, and the host given the top of it.
@OptIn(ExperimentalTestApi::class)
class OverlaysTest {
    private val received = mutableListOf<KompotAction>()
    private val performed = mutableListOf<String>()
    private val app = KompotActionHandler { received += it }

    private fun screen(
        raise: KompotAction,
        host: @Composable (KompotOverlays, KompotActionHandler) -> Unit = { overlays, handler -> KompotOverlayHost(overlays, handler) },
    ) =
        @Composable {
            val overlays = remember { KompotOverlays() }
            val scope = rememberCoroutineScope()
            val handler =
                remember {
                    app.withOverlays(overlays).withPerform(scope) { url, _ ->
                        performed += url
                        NavigateAction(deeplink = "app://done")
                    }
                }
            MaterialTheme {
                CompositionLocalProvider(
                    LocalKompotDesignSystem provides Material3DesignSystem(),
                    LocalKompotRegistry provides KompotRegistry(kompotCoreRenderers + kompotStandardRenderers),
                ) {
                    Column { Button(onClick = { handler.handle(raise) }) { Text("raise") } }
                    host(overlays, handler)
                }
            }
        }

    private val sheet =
        ColumnComponent(
            id = "sheet",
            children =
                listOf(
                    TextComponent(id = "choose", text = "Choose a column"),
                    ButtonComponent(id = "shut", text = "Done", action = CloseAction),
                ),
        )

    @Test
    fun `agreeing runs the guarded action, through the whole chain`() =
        runDesktopComposeUiTest {
            setContent(screen(ConfirmAction(question = "Delete the board?", action = PerformAction(url = "/boards/3/delete"))))
            onNodeWithText("raise").performClick()
            waitForIdle()
            onNodeWithText("Delete the board?").assertIsDisplayed()
            onNodeWithText("OK").performClick()
            waitForIdle()
            assertEquals(listOf("/boards/3/delete"), performed, "the perform behind the question was sent")
            onNodeWithText("Delete the board?").assertDoesNotExist()
        }

    @Test
    fun `refusing runs nothing`() =
        runDesktopComposeUiTest {
            setContent(
                screen(ConfirmAction(question = "Delete the board?", action = PerformAction(url = "/boards/3/delete"), cancelLabel = "Keep it")),
            )
            onNodeWithText("raise").performClick()
            waitForIdle()
            onNodeWithText("Keep it").performClick()
            waitForIdle()
            assertEquals(emptyList(), performed)
        }

    @Test
    fun `a presented dialog shows its tree, and close inside it closes the dialog and nothing else`() =
        runDesktopComposeUiTest {
            setContent(screen(PresentAction(content = sheet)))
            onNodeWithText("raise").performClick()
            waitForIdle()
            onNodeWithText("Choose a column").assertIsDisplayed()
            onNodeWithText("Done").performClick()
            waitForIdle()
            onNodeWithText("Choose a column").assertDoesNotExist()
            assertTrue(CloseAction !in received, "close was taken by the dialog, not also sent to the app: $received")
        }

    @Test
    fun `a presented sheet shows its tree`() =
        runDesktopComposeUiTest {
            setContent(screen(PresentAction(content = sheet, kind = "sheet")))
            onNodeWithText("raise").performClick()
            waitForIdle()
            onNodeWithText("Choose a column").assertIsDisplayed()
        }

    @Test
    fun `close with nothing open still reaches the application`() =
        runDesktopComposeUiTest {
            setContent(screen(CloseAction))
            onNodeWithText("raise").performClick()
            waitForIdle()
            assertEquals(listOf<KompotAction>(CloseAction), received)
        }

    @Test
    fun `a navigation from inside a sheet closes the sheet and reaches the application once`() =
        runDesktopComposeUiTest {
            val pay =
                ColumnComponent(
                    id = "confirm",
                    children =
                        listOf(
                            TextComponent(id = "total", text = "Pay 12 dollars"),
                            ButtonComponent(id = "pay", text = "Pay", action = NavigateAction(deeplink = "app://result")),
                        ),
                )
            setContent(screen(PresentAction(content = pay, kind = "sheet")))
            onNodeWithText("raise").performClick()
            waitForIdle()
            onNodeWithText("Pay").performClick()
            waitForIdle()
            onNodeWithText("Pay 12 dollars").assertDoesNotExist()
            assertEquals(listOf<KompotAction>(NavigateAction(deeplink = "app://result")), received.filterIsInstance<NavigateAction>())
        }

    @Test
    fun `a navigation agreed to behind a question closes the question and the tree under it`() =
        runDesktopComposeUiTest {
            val leave =
                ColumnComponent(
                    id = "leave",
                    children =
                        listOf(
                            TextComponent(id = "draft", text = "Unsaved draft"),
                            ButtonComponent(
                                id = "discard",
                                text = "Discard",
                                action = ConfirmAction(question = "Discard the draft?", action = NavigateAction(deeplink = "app://home")),
                            ),
                        ),
                )
            setContent(screen(PresentAction(content = leave)))
            onNodeWithText("raise").performClick()
            waitForIdle()
            onNodeWithText("Discard").performClick()
            waitForIdle()
            onNodeWithText("OK").performClick()
            waitForIdle()
            onNodeWithText("Discard the draft?").assertDoesNotExist()
            onNodeWithText("Unsaved draft").assertDoesNotExist()
            assertEquals(listOf<KompotAction>(NavigateAction(deeplink = "app://home")), received.filterIsInstance<NavigateAction>())
        }

    // A design system with its own sheet: everything it needs is the public state and the three answers.
    // Nothing here may touch what the Material host can reach through `internal`.
    @Test
    fun `a host written against the public state draws the layer and answers for it`() =
        runDesktopComposeUiTest {
            val question = ConfirmAction(question = "Delete the board?", action = PerformAction(url = "/boards/3/delete"))
            val tree =
                ColumnComponent(
                    id = "tree",
                    children =
                        listOf(
                            TextComponent(id = "heading", text = "Board actions"),
                            ButtonComponent(id = "delete", text = "Delete", action = question),
                        ),
                )
            setContent(
                screen(PresentAction(content = tree, kind = "sheet")) { overlays, handler ->
                    val registry = LocalKompotRegistry.current
                    val form = remember { FormController(FormSchema(formId = "own", fields = emptyList())) }
                    Column {
                        overlays.presented?.let { presented ->
                            Text("own sheet")
                            registry.RenderNode(presented.content, handler, form)
                            Button(onClick = { overlays.dismiss() }) { Text("swipe away") }
                        }
                        overlays.asking?.let { asking ->
                            Text(asking.question)
                            Button(onClick = { overlays.agree(handler) }) { Text("yes") }
                            Button(onClick = { overlays.refuse() }) { Text("no") }
                        }
                    }
                },
            )
            onNodeWithText("raise").performClick()
            waitForIdle()
            onNodeWithText("own sheet").assertIsDisplayed()
            onNodeWithText("swipe away").performClick()
            waitForIdle()
            onNodeWithText("own sheet").assertDoesNotExist()

            onNodeWithText("raise").performClick()
            waitForIdle()
            onNodeWithText("Delete").performClick()
            waitForIdle()
            onNodeWithText("no").performClick()
            waitForIdle()
            assertEquals(emptyList(), performed, "refusing ran nothing")
            onNodeWithText("own sheet").assertIsDisplayed()
            onNodeWithText("Delete").performClick()
            waitForIdle()
            onNodeWithText("yes").performClick()
            waitForIdle()
            assertEquals(listOf("/boards/3/delete"), performed, "agreeing ran the guarded action through the chain")
            // The perform answered with a navigation, and a navigation takes the layer with it.
            onNodeWithText("own sheet").assertDoesNotExist()
        }
}
