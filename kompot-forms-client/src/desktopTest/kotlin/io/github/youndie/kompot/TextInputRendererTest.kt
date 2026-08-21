package io.github.youndie.kompot

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.requestFocus
import io.github.youndie.kompot.forms.TextInputComponent
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.form.FormSchema
import io.github.youndie.kompot.form.standard.BooleanValue
import io.github.youndie.kompot.form.standard.CheckboxFieldDefinition
import io.github.youndie.kompot.form.standard.EqualsCondition
import io.github.youndie.kompot.form.standard.RequiredRule
import io.github.youndie.kompot.form.standard.TextFieldDefinition
import io.github.youndie.kompot.form.standard.TextValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class TextInputRendererTest {
    @Test
    fun `renders label, placeholder and the current field value`() =
        runFormsComposeUiTest {
            val controller =
                FormController(
                    FormSchema("form", fields = listOf(TextFieldDefinition("name", rules = emptyList()))),
                    initialValues = mapOf("name" to TextValue("Ada")),
                )

            setContent {
                TestKompotTheme {
                    TextInputRenderer().Render(
                        component = TextInputComponent(id = "c", fieldId = "name", label = "Name", placeholder = "Enter a name"),
                        actionHandler = recordingActionHandler(),
                        formController = controller,
                    )
                }
            }

            onNodeWithText("Name").assertIsDisplayed().assertTextContains("Ada")
        }

    @Test
    fun `typing a value updates the FormController with the raw value`() =
        runFormsComposeUiTest {
            val controller = FormController(FormSchema("form", fields = listOf(TextFieldDefinition("name", rules = emptyList()))))

            setContent {
                TestKompotTheme {
                    TextInputRenderer().Render(
                        component = TextInputComponent(id = "c", fieldId = "name", label = "Name"),
                        actionHandler = recordingActionHandler(),
                        formController = controller,
                    )
                }
            }

            onNodeWithText("Name").performTextInput("Grace")
            waitForIdle()

            assertEquals("Grace", controller.getTypedState<TextValue>("name").value?.text)
        }

    @Test
    fun `a mask strips non-digit characters and truncates to the mask's raw length`() =
        runFormsComposeUiTest {
            val controller = FormController(FormSchema("form", fields = listOf(TextFieldDefinition("phone", rules = emptyList()))))

            setContent {
                TestKompotTheme {
                    TextInputRenderer().Render(
                        component =
                            TextInputComponent(
                                id = "c",
                                fieldId = "phone",
                                label = "Phone",
                                mask = "+998 (##) ###-##-##",
                            ),
                        actionHandler = recordingActionHandler(),
                        formController = controller,
                    )
                }
            }

            onNodeWithText("Phone").performTextInput("ab90123456789xyz")
            waitForIdle()

            assertEquals("901234567", controller.getTypedState<TextValue>("phone").value?.text)
        }

    @Test
    fun `uppercase transforms both the display and the stored value`() =
        runFormsComposeUiTest {
            val controller = FormController(FormSchema("form", fields = listOf(TextFieldDefinition("swift", rules = emptyList()))))

            setContent {
                TestKompotTheme {
                    TextInputRenderer().Render(
                        component = TextInputComponent(id = "c", fieldId = "swift", label = "SWIFT", uppercase = true),
                        actionHandler = recordingActionHandler(),
                        formController = controller,
                    )
                }
            }

            onNodeWithText("SWIFT").performTextInput("abcd1234")
            waitForIdle()

            assertEquals("ABCD1234", controller.getTypedState<TextValue>("swift").value?.text)
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
                                CheckboxFieldDefinition("show_field"),
                                TextFieldDefinition(
                                    "extra",
                                    rules = emptyList(),
                                    visibleIf = EqualsCondition("show_field", BooleanValue(true)),
                                ),
                            ),
                    ),
                )

            setContent {
                TestKompotTheme {
                    TextInputRenderer().Render(
                        component = TextInputComponent(id = "c", fieldId = "extra", label = "Extra field"),
                        actionHandler = recordingActionHandler(),
                        formController = controller,
                    )
                }
            }

            onAllNodesWithText("Extra field").assertCountEquals(0)
        }

    @Test
    fun `losing focus after leaving a required field empty shows its validation error`() =
        runFormsComposeUiTest {
            val controller =
                FormController(
                    FormSchema(
                        "form",
                        fields =
                            listOf(
                                TextFieldDefinition("name", rules = listOf(RequiredRule("Required"))),
                                TextFieldDefinition("other", rules = emptyList()),
                            ),
                    ),
                )

            setContent {
                TestKompotTheme {
                    Column {
                        TextInputRenderer().Render(
                            component = TextInputComponent(id = "c1", fieldId = "name", label = "Name"),
                            actionHandler = recordingActionHandler(),
                            formController = controller,
                        )
                        TextInputRenderer().Render(
                            component = TextInputComponent(id = "c2", fieldId = "other", label = "Other"),
                            actionHandler = recordingActionHandler(),
                            formController = controller,
                        )
                    }
                }
            }

            assertNull(controller.getTypedState<TextValue>("name").error)

            onNodeWithText("Name").requestFocus()
            waitForIdle()
            onNodeWithText("Other").requestFocus()
            waitForIdle()

            assertEquals("Required", controller.getTypedState<TextValue>("name").error)
            onNodeWithText("Required").assertIsDisplayed()
        }
}
