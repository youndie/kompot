package io.github.youndie.kompot

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.kompot.commands.PerformAction
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.form.FormSchema
import io.github.youndie.kompot.forms.SubmitFormAction
import io.github.youndie.kompot.standard.ButtonComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.SequenceAction
import kotlin.test.Test
import kotlin.test.assertEquals

// An unknown action in the server's ANSWER is reported — once — like one raised by a node (B-60).
// The answer enters the chain past the wrapper RenderNode puts around every node, which is why it used
// to be reported nowhere; the same sink sits on both roads, so this also holds that nothing is counted
// twice.
@OptIn(ExperimentalTestApi::class)
class AnswerReportingTest {
    private val reported = mutableListOf<String>()
    private val sink = KompotDegradationSink { _, type, _ -> reported += type }

    private fun raising(
        raise: KompotAction,
        answer: KompotAction,
    ) = @androidx.compose.runtime.Composable {
        val scope = rememberCoroutineScope()
        val controller = remember { FormController(FormSchema(formId = "login", fields = emptyList())) }
        val handler =
            remember {
                KompotActionHandler {}
                    .withLoginSubmit(scope, controller, "login", sink) { answer }
                    .withPerform(scope, sink) { _, _ -> answer }
            }
        TestKompotTheme {
            CompositionLocalProvider(
                LocalKompotRegistry provides KompotRegistry(kompotCoreRenderers + kompotStandardRenderers),
                LocalKompotDegradationSink provides sink,
            ) {
                LocalKompotRegistry.current.RenderNode(ButtonComponent(id = "go", text = "go", action = raise), handler, controller)
            }
        }
    }

    @Test
    fun `a perform answered with an unknown action reports it once`() =
        runDesktopComposeUiTest {
            setContent(raising(PerformAction(url = "/cards/7/archive"), UnknownAction(originalType = "vibrate")))
            onNodeWithText("go").performClick()
            waitForIdle()
            assertEquals(listOf("vibrate"), reported)
        }

    @Test
    fun `an unknown part of an answered sequence is reported once`() =
        runDesktopComposeUiTest {
            setContent(
                raising(
                    PerformAction(url = "/cards/7/archive"),
                    SequenceAction(listOf(NavigateAction(deeplink = "app://board"), UnknownAction(originalType = "haptic"))),
                ),
            )
            onNodeWithText("go").performClick()
            waitForIdle()
            assertEquals(listOf("haptic"), reported)
        }

    @Test
    fun `a submit answered with an unknown action reports it once`() =
        runDesktopComposeUiTest {
            setContent(raising(SubmitFormAction(formId = "login"), UnknownAction(originalType = "biometric_prompt")))
            onNodeWithText("go").performClick()
            waitForIdle()
            assertEquals(listOf("biometric_prompt"), reported)
        }

    // The road that was already reported must not now be reported twice: a node raising an unknown
    // action goes through the renderer's wrapper, and the answer wrappers see no answer in it.
    @Test
    fun `an unknown action raised by a node is still reported once, not twice`() =
        runDesktopComposeUiTest {
            setContent(raising(UnknownAction(originalType = "swipe_card"), NavigateAction(deeplink = "app://unused")))
            onNodeWithText("go").performClick()
            waitForIdle()
            assertEquals(listOf("swipe_card"), reported)
        }
}
