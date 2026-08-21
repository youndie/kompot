package io.github.youndie.kompot

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.kompot.standard.TextComponent
import io.github.youndie.kompot.wizard.PrevStepAction
import io.github.youndie.kompot.wizard.WizardScreenComponent
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class WizardScreenRendererTest {
    @Test
    fun `renders the step content`() =
        runDesktopComposeUiTest {
            setContent {
                TestKompotTheme {
                    WizardScreenRenderer().Render(
                        component =
                            WizardScreenComponent(
                                id = "step",
                                formId = "mortgage",
                                stepId = "loan_details",
                                stepIndex = 0,
                                content = TextComponent(id = "t", text = "Enter an amount"),
                            ),
                        actionHandler = recordingActionHandler(),
                        formController = testFormController(),
                    )
                }
            }

            onNodeWithText("Enter an amount").assertIsDisplayed()
        }

    @Test
    fun `no back control is rendered when canGoBack is false`() =
        runDesktopComposeUiTest {
            setContent {
                TestKompotTheme {
                    WizardScreenRenderer().Render(
                        component =
                            WizardScreenComponent(
                                id = "step",
                                formId = "mortgage",
                                stepId = "loan_details",
                                stepIndex = 0,
                                canGoBack = false,
                                content = TextComponent(id = "t", text = "Enter an amount"),
                            ),
                        actionHandler = recordingActionHandler(),
                        formController = testFormController(),
                    )
                }
            }

            onAllNodesWithText("Back").assertCountEquals(0)
        }

    @Test
    fun `clicking back invokes the handler with PrevStepAction for this formId`() =
        runDesktopComposeUiTest {
            var handled: KompotAction? = null

            setContent {
                TestKompotTheme {
                    WizardScreenRenderer().Render(
                        component =
                            WizardScreenComponent(
                                id = "step",
                                formId = "mortgage",
                                stepId = "collateral",
                                stepIndex = 1,
                                canGoBack = true,
                                content = TextComponent(id = "t", text = "Step 2"),
                            ),
                        actionHandler = recordingActionHandler { handled = it },
                        formController = testFormController(),
                    )
                }
            }

            onNodeWithText("Back").performClick()
            assertEquals(PrevStepAction("mortgage"), handled)
        }
}
