package io.github.youndie.kompot

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.youndie.kompot.forms.SelectInputComponent
import io.github.youndie.kompot.forms.SelectOption
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.form.FormSchema
import io.github.youndie.kompot.form.standard.BooleanValue
import io.github.youndie.kompot.form.standard.CheckboxFieldDefinition
import io.github.youndie.kompot.form.standard.EntityValue
import io.github.youndie.kompot.form.standard.EqualsCondition
import io.github.youndie.kompot.form.standard.SelectionFieldDefinition
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SelectInputRendererTest {
    private val options =
        listOf(
            SelectOption(id = "usd", label = "US dollar", rawMetadata = mapOf("currency" to "USD")),
            SelectOption(id = "uzs", label = "Euro"),
        )

    @Test
    fun `renders label and the currently selected option's label`() =
        runFormsComposeUiTest {
            val controller =
                FormController(
                    FormSchema("form", fields = listOf(SelectionFieldDefinition("currency"))),
                    initialValues = mapOf("currency" to EntityValue(id = "usd", title = "US dollar")),
                )

            setContent {
                TestKompotTheme {
                    SelectInputRenderer().Render(
                        component = SelectInputComponent(id = "c", fieldId = "currency", label = "Currency", options = options),
                        actionHandler = recordingActionHandler(),
                        formController = controller,
                    )
                }
            }

            onNodeWithText("Currency").assertIsDisplayed()
            onNodeWithText("US dollar").assertIsDisplayed()
        }

    @Test
    fun `opening the dropdown and clicking an option stores it as EntityValue with its rawMetadata`() =
        runFormsComposeUiTest {
            val controller = FormController(FormSchema("form", fields = listOf(SelectionFieldDefinition("currency"))))

            setContent {
                TestKompotTheme {
                    SelectInputRenderer().Render(
                        component = SelectInputComponent(id = "c", fieldId = "currency", label = "Currency", options = options),
                        actionHandler = recordingActionHandler(),
                        formController = controller,
                    )
                }
            }

            onNodeWithText("Currency").performClick()
            waitForIdle()
            onNodeWithText("US dollar").performClick()
            waitForIdle()

            assertEquals(
                EntityValue(id = "usd", title = "US dollar", rawMetadata = mapOf("currency" to "USD")),
                controller.getTypedState<EntityValue>("currency").value,
            )
        }

    @Test
    fun `picking a second option replaces the first selection rather than appending to it`() =
        runFormsComposeUiTest {
            val controller =
                FormController(
                    FormSchema("form", fields = listOf(SelectionFieldDefinition("currency"))),
                    initialValues = mapOf("currency" to EntityValue(id = "usd", title = "US dollar", rawMetadata = mapOf("currency" to "USD"))),
                )

            setContent {
                TestKompotTheme {
                    SelectInputRenderer().Render(
                        component = SelectInputComponent(id = "c", fieldId = "currency", label = "Currency", options = options),
                        actionHandler = recordingActionHandler(),
                        formController = controller,
                    )
                }
            }

            onNodeWithText("US dollar").performClick()
            waitForIdle()
            onNodeWithText("Euro").performClick()
            waitForIdle()

            assertEquals(
                EntityValue(id = "uzs", title = "Euro"),
                controller.getTypedState<EntityValue>("currency").value,
            )
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
                                SelectionFieldDefinition("currency", visibleIf = EqualsCondition("gate", BooleanValue(true))),
                            ),
                    ),
                )

            setContent {
                TestKompotTheme {
                    SelectInputRenderer().Render(
                        component = SelectInputComponent(id = "c", fieldId = "currency", label = "Currency", options = options),
                        actionHandler = recordingActionHandler(),
                        formController = controller,
                    )
                }
            }

            onAllNodesWithText("Currency").assertCountEquals(0)
        }
}
