package io.github.youndie.kompot

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import io.github.youndie.kompot.forms.ReadOnlyFieldComponent
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ReadOnlyFieldRendererTest {
    @Test
    fun `renders label, value and helper text`() =
        runFormsComposeUiTest {
            setContent {
                TestKompotTheme {
                    ReadOnlyFieldRenderer().Render(
                        component =
                            ReadOnlyFieldComponent(
                                id = "c",
                                label = "Sender",
                                value = "Ada Lovelace",
                                helperText = "From your profile",
                            ),
                        actionHandler = recordingActionHandler(),
                        formController = testFormController(),
                    )
                }
            }

            onNodeWithText("Sender").assertIsDisplayed()
            onNodeWithText("Ada Lovelace").assertIsDisplayed()
            onNodeWithText("From your profile").assertIsDisplayed()
        }

    @Test
    fun `the field is disabled`() =
        runFormsComposeUiTest {
            setContent {
                TestKompotTheme {
                    ReadOnlyFieldRenderer().Render(
                        component = ReadOnlyFieldComponent(id = "c", label = "Sender", value = "Ada Lovelace"),
                        actionHandler = recordingActionHandler(),
                        formController = testFormController(),
                    )
                }
            }

            onNodeWithText("Sender").assertIsNotEnabled()
        }

    @Test
    fun `omitting helperText renders no supporting text node`() =
        runFormsComposeUiTest {
            setContent {
                TestKompotTheme {
                    ReadOnlyFieldRenderer().Render(
                        component = ReadOnlyFieldComponent(id = "c", label = "Sender", value = "Ada Lovelace"),
                        actionHandler = recordingActionHandler(),
                        formController = testFormController(),
                    )
                }
            }

            onNodeWithText("Sender").assertIsDisplayed()
        }
}
