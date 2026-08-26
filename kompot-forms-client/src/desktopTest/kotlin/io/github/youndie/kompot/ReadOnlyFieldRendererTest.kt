package io.github.youndie.kompot

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.form.FormPatch
import io.github.youndie.kompot.form.FormSchema
import io.github.youndie.kompot.form.standard.EqualsCondition
import io.github.youndie.kompot.form.standard.TextFieldDefinition
import io.github.youndie.kompot.form.standard.TextValue
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

// A value the SERVER computes as the form changes had nowhere to live: every component a patch could
// reach was editable, so a price was either something a person could type into or correct once and
// stale after.
@OptIn(ExperimentalTestApi::class)
class BoundReadOnlyFieldTest {
    private fun controller() =
        FormController(
            FormSchema(
                formId = "package",
                fields = listOf(TextFieldDefinition(fieldId = "price", rules = emptyList())),
            ),
            initialValues = mapOf("price" to TextValue("0,00 €")),
        )

    @Test
    fun `a patch reaches the field it names`() =
        runFormsComposeUiTest {
            val controller = controller()
            setContent {
                TestKompotTheme {
                    ReadOnlyFieldRenderer().Render(
                        component = ReadOnlyFieldComponent(id = "r", label = "Price", value = "—", fieldId = "price"),
                        actionHandler = recordingActionHandler(),
                        formController = controller,
                    )
                }
            }

            onNodeWithText("0,00 €").assertExists()

            controller.applyPatch(FormPatch(updates = mapOf("price" to TextValue("12,40 €"))))
            waitForIdle()

            onNodeWithText("12,40 €").assertExists()
        }

    // The control: the same patch, the same field, and no fieldId on the component. This is what
    // every tree written before the field says, and it must keep saying it.
    @Test
    fun `without a fieldId the server's own value is what is drawn`() =
        runFormsComposeUiTest {
            val controller = controller()
            setContent {
                TestKompotTheme {
                    ReadOnlyFieldRenderer().Render(
                        component = ReadOnlyFieldComponent(id = "r", label = "Price", value = "0,00 €"),
                        actionHandler = recordingActionHandler(),
                        formController = controller,
                    )
                }
            }

            controller.applyPatch(FormPatch(updates = mapOf("price" to TextValue("12,40 €"))))
            waitForIdle()

            onNodeWithText("0,00 €").assertExists()
        }

    // Bound means bound: a field hidden by its condition takes its display with it, exactly as an
    // editable one does.
    @Test
    fun `a hidden field is not drawn`() =
        runFormsComposeUiTest {
            val controller =
                FormController(
                    FormSchema(
                        formId = "package",
                        fields =
                            listOf(
                                TextFieldDefinition(fieldId = "kind", rules = emptyList()),
                                TextFieldDefinition(
                                    fieldId = "price",
                                    rules = emptyList(),
                                    visibleIf = EqualsCondition(fieldId = "kind", expectedValue = TextValue("custom")),
                                ),
                            ),
                    ),
                    initialValues = mapOf("price" to TextValue("12,40 €")),
                )

            setContent {
                TestKompotTheme {
                    ReadOnlyFieldRenderer().Render(
                        component = ReadOnlyFieldComponent(id = "r", label = "Price", value = "—", fieldId = "price"),
                        actionHandler = recordingActionHandler(),
                        formController = controller,
                    )
                }
            }

            onNodeWithText("12,40 €").assertDoesNotExist()
        }
}
