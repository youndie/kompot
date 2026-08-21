package io.github.youndie.kompot

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.youndie.kompot.forms.RadioGroupComponent
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
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class RadioGroupRendererTest {
    private val options =
        listOf(
            SelectOption(id = "individual", label = "Individual"),
            SelectOption(id = "business", label = "Company", rawMetadata = mapOf("kind" to "b2b")),
        )

    @Test
    fun `renders the group label and every option`() =
        runFormsComposeUiTest {
            val controller = FormController(FormSchema("form", fields = listOf(SelectionFieldDefinition("payer_type"))))

            setContent {
                TestKompotTheme {
                    RadioGroupRenderer().Render(
                        component = RadioGroupComponent(id = "c", fieldId = "payer_type", label = "Customer type", options = options),
                        actionHandler = recordingActionHandler(),
                        formController = controller,
                    )
                }
            }

            onNodeWithText("Customer type").assertIsDisplayed()
            onNodeWithText("Individual").assertIsDisplayed()
            onNodeWithText("Company").assertIsDisplayed()
        }

    @Test
    fun `clicking an option stores it as EntityValue with its rawMetadata and immediately marks the field as blurred`() =
        runFormsComposeUiTest {
            val controller = FormController(FormSchema("form", fields = listOf(SelectionFieldDefinition("payer_type"))))

            setContent {
                TestKompotTheme {
                    RadioGroupRenderer().Render(
                        component = RadioGroupComponent(id = "c", fieldId = "payer_type", label = "Customer type", options = options),
                        actionHandler = recordingActionHandler(),
                        formController = controller,
                    )
                }
            }

            onNodeWithText("Company").performClick()
            waitForIdle()

            assertEquals(
                EntityValue(id = "business", title = "Company", rawMetadata = mapOf("kind" to "b2b")),
                controller.getTypedState<EntityValue>("payer_type").value,
            )
            assertEquals(true, controller.getTypedState<EntityValue>("payer_type").changed)
        }

    @Test
    fun `picking a different option replaces the previous selection`() =
        runFormsComposeUiTest {
            val controller =
                FormController(
                    FormSchema("form", fields = listOf(SelectionFieldDefinition("payer_type"))),
                    initialValues = mapOf("payer_type" to EntityValue(id = "individual", title = "Individual")),
                )

            setContent {
                TestKompotTheme {
                    RadioGroupRenderer().Render(
                        component = RadioGroupComponent(id = "c", fieldId = "payer_type", label = "Customer type", options = options),
                        actionHandler = recordingActionHandler(),
                        formController = controller,
                    )
                }
            }

            onNodeWithText("Company").performClick()
            waitForIdle()

            assertEquals("business", controller.getTypedState<EntityValue>("payer_type").value?.id)
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
                                SelectionFieldDefinition("payer_type", visibleIf = EqualsCondition("gate", BooleanValue(true))),
                            ),
                    ),
                )

            setContent {
                TestKompotTheme {
                    RadioGroupRenderer().Render(
                        component = RadioGroupComponent(id = "c", fieldId = "payer_type", label = "Customer type", options = options),
                        actionHandler = recordingActionHandler(),
                        formController = controller,
                    )
                }
            }

            onAllNodesWithText("Customer type").assertCountEquals(0)
            assertNull(controller.getTypedState<EntityValue>("payer_type").value)
        }
}
