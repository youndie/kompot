package io.github.youndie.kompot

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.kompot.commands.PerformAction
import io.github.youndie.kompot.standard.ButtonComponent
import io.github.youndie.kompot.standard.CopyTextAction
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.SequenceAction
import kotlin.test.Test
import kotlin.test.assertEquals

// sequence (SPEC.md §16.4), run by withSequences in the chain an application builds: withSequences
// inside withPerform, its followUp the top of the chain.
@OptIn(ExperimentalTestApi::class)
class SequenceTest {
    private val received = mutableListOf<KompotAction>()
    private val performed = mutableListOf<String>()
    private val app = KompotActionHandler { if (it !is SequenceAction) received += it }

    private val a = NavigateAction(deeplink = "app://a")
    private val b = CopyTextAction(text = "b")
    private val c = NavigateAction(deeplink = "app://c")

    private fun raising(
        raise: KompotAction,
        answers: Map<String, KompotAction> = emptyMap(),
        reported: MutableList<String> = mutableListOf(),
    ) = @androidx.compose.runtime.Composable {
        val scope = rememberCoroutineScope()
        val handler =
            remember {
                lateinit var top: KompotActionHandler
                top =
                    app.withSequences { top.handle(it) }.withPerform(scope) { url, _ ->
                        performed += url
                        answers.getValue(url)
                    }
                top
            }
        TestKompotTheme {
            CompositionLocalProvider(
                LocalKompotRegistry provides KompotRegistry(kompotCoreRenderers + kompotStandardRenderers),
                LocalKompotDegradationSink provides KompotDegradationSink { _, type, _ -> reported += type },
            ) {
                LocalKompotRegistry.current.RenderNode(ButtonComponent(id = "go", text = "go", action = raise), handler, testFormController())
            }
        }
    }

    @Test
    fun `the parts run in order`() =
        runDesktopComposeUiTest {
            setContent(raising(SequenceAction(listOf(a, b, c))))
            onNodeWithText("go").performClick()
            waitForIdle()
            assertEquals(listOf(a, b, c), received)
        }

    @Test
    fun `a sequence inside a sequence runs too, because its parts go back to the top`() =
        runDesktopComposeUiTest {
            setContent(raising(SequenceAction(listOf(a, SequenceAction(listOf(b, c))))))
            onNodeWithText("go").performClick()
            waitForIdle()
            assertEquals(listOf(a, b, c), received)
        }

    // The case the item is about: an answer that is several actions. And a perform among the parts is
    // sent, which it would not be if the parts went to the handler withSequences wraps.
    @Test
    fun `a perform answering with a sequence runs every part, a perform among them`() =
        runDesktopComposeUiTest {
            setContent(
                raising(
                    PerformAction(url = "/cards/7/archive"),
                    answers =
                        mapOf(
                            "/cards/7/archive" to SequenceAction(listOf(a, PerformAction(url = "/boards/3/refresh"))),
                            "/boards/3/refresh" to c,
                        ),
                ),
            )
            onNodeWithText("go").performClick()
            waitForIdle()
            assertEquals(listOf("/cards/7/archive", "/boards/3/refresh"), performed)
            assertEquals(listOf(a, c), received.filter { it !is PerformAction })
        }

    @Test
    fun `an unknown part is reported and the rest still runs`() =
        runDesktopComposeUiTest {
            val reported = mutableListOf<String>()
            setContent(raising(SequenceAction(listOf(a, UnknownAction(originalType = "vibrate"), c)), reported = reported))
            onNodeWithText("go").performClick()
            waitForIdle()
            assertEquals(listOf("vibrate"), reported)
            assertEquals(listOf(a, UnknownAction(originalType = "vibrate"), c), received)
        }
}
