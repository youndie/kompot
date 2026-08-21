package io.github.youndie.kompot

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.youndie.kompot.forms.CheckboxInputComponent
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.form.FormSchema
import io.github.youndie.kompot.form.standard.BooleanValue
import io.github.youndie.kompot.form.standard.CheckboxFieldDefinition
import io.github.youndie.kompot.form.standard.EqualsCondition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalTestApi::class)
class CheckboxInputRendererTest {
    @Test
    fun `renders label and starts unchecked when there is no initial value`() =
        runFormsComposeUiTest {
            val controller = FormController(FormSchema("form", fields = listOf(CheckboxFieldDefinition("auto"))))

            setContent {
                TestKompotTheme {
                    CheckboxInputRenderer().Render(
                        component = CheckboxInputComponent(id = "c", fieldId = "auto", label = "Auto-numbering"),
                        actionHandler = recordingActionHandler(),
                        formController = controller,
                    )
                }
            }

            onNodeWithText("Auto-numbering").assertIsDisplayed()
            assertFalse(controller.getTypedState<BooleanValue>("auto").value?.value ?: false)
        }

    @Test
    fun `clicking anywhere on the row toggles the value`() =
        runFormsComposeUiTest {
            val controller = FormController(FormSchema("form", fields = listOf(CheckboxFieldDefinition("auto"))))

            setContent {
                TestKompotTheme {
                    CheckboxInputRenderer().Render(
                        component = CheckboxInputComponent(id = "c", fieldId = "auto", label = "Auto-numbering"),
                        actionHandler = recordingActionHandler(),
                        formController = controller,
                    )
                }
            }

            onNodeWithText("Auto-numbering").performClick()
            waitForIdle()
            assertEquals(true, controller.getTypedState<BooleanValue>("auto").value?.value)

            onNodeWithText("Auto-numbering").performClick()
            waitForIdle()
            assertEquals(false, controller.getTypedState<BooleanValue>("auto").value?.value)
        }

    @Test
    fun `the field is not rendered at all when its visibleIf condition is not satisfied`() =
        runFormsComposeUiTest {
            val controller =
                FormController(
                    FormSchema(
                        "form",
                        fields =
                            listOf(
                                CheckboxFieldDefinition("gate"),
                                CheckboxFieldDefinition("auto", visibleIf = EqualsCondition("gate", BooleanValue(true))),
                            ),
                    ),
                )

            setContent {
                TestKompotTheme {
                    CheckboxInputRenderer().Render(
                        component = CheckboxInputComponent(id = "c", fieldId = "auto", label = "Auto-numbering"),
                        actionHandler = recordingActionHandler(),
                        formController = controller,
                    )
                }
            }

            onAllNodesWithText("Auto-numbering").assertCountEquals(0)
        }
}
