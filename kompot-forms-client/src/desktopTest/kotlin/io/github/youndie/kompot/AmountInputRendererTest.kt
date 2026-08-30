package io.github.youndie.kompot

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.requestFocus
import io.github.youndie.kompot.forms.AmountInputComponent
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.form.FormSchema
import io.github.youndie.kompot.form.standard.AmountFieldDefinition
import io.github.youndie.kompot.form.standard.AmountValue
import io.github.youndie.kompot.form.standard.BooleanValue
import io.github.youndie.kompot.form.standard.CheckboxFieldDefinition
import io.github.youndie.kompot.form.standard.EntityValue
import io.github.youndie.kompot.form.standard.EqualsCondition
import io.github.youndie.kompot.form.standard.RequiredRule
import io.github.youndie.kompot.form.standard.SelectionFieldDefinition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class AmountInputRendererTest {
    @Test
    fun `renders label and the current amount`() =
        runFormsComposeUiTest {
            val controller =
                FormController(
                    FormSchema("form", fields = listOf(AmountFieldDefinition("amount", rules = emptyList()))),
                    initialValues = mapOf("amount" to AmountValue(150_000L)),
                )

            setContent {
                TestKompotTheme {
                    AmountInputRenderer().Render(
                        component = AmountInputComponent(id = "c", fieldId = "amount", label = "Amount", currencySuffix = "UZS"),
                        actionHandler = recordingActionHandler(),
                        formController = controller,
                    )
                }
            }

            onNodeWithText("Amount").assertIsDisplayed()
        }

    @Test
    fun `typing digits updates the FormController with the numeric value`() =
        runFormsComposeUiTest {
            val controller = FormController(FormSchema("form", fields = listOf(AmountFieldDefinition("amount", rules = emptyList()))))

            setContent {
                TestKompotTheme {
                    AmountInputRenderer().Render(
                        component = AmountInputComponent(id = "c", fieldId = "amount", label = "Amount"),
                        actionHandler = recordingActionHandler(),
                        formController = controller,
                    )
                }
            }

            onNodeWithText("Amount").performTextInput("150000")
            waitForIdle()

            assertEquals(150_000L, controller.getTypedState<AmountValue>("amount").value?.long)
        }

    @Test
    fun `clearing the input resets the amount to zero instead of leaving stale digits`() =
        runFormsComposeUiTest {
            val controller =
                FormController(
                    FormSchema("form", fields = listOf(AmountFieldDefinition("amount", rules = emptyList()))),
                    initialValues = mapOf("amount" to AmountValue(500L)),
                )

            setContent {
                TestKompotTheme {
                    AmountInputRenderer().Render(
                        component = AmountInputComponent(id = "c", fieldId = "amount", label = "Amount"),
                        actionHandler = recordingActionHandler(),
                        formController = controller,
                    )
                }
            }

            onNodeWithText("Amount").performTextClearance()
            waitForIdle()

            assertEquals(0L, controller.getTypedState<AmountValue>("amount").value?.long)
        }

    @Test
    fun `currencyFromField locally derives the currency from another field's EntityValue metadata`() =
        runFormsComposeUiTest {
            val controller =
                FormController(
                    FormSchema(
                        "form",
                        fields =
                            listOf(
                                SelectionFieldDefinition("account"),
                                AmountFieldDefinition("amount", rules = emptyList()),
                            ),
                    ),
                    initialValues = mapOf("amount" to AmountValue(1000L)),
                )

            setContent {
                TestKompotTheme {
                    AmountInputRenderer().Render(
                        component = AmountInputComponent(id = "c", fieldId = "amount", label = "Amount", currencyFromField = "account"),
                        actionHandler = recordingActionHandler(),
                        formController = controller,
                    )
                }
            }

            controller.onValueChanged("account", EntityValue(id = "acc_1", title = "Main source", rawMetadata = mapOf("currency" to "USD")))
            waitForIdle()

            assertEquals("USD", controller.getTypedState<AmountValue>("amount").value?.currency)
            assertEquals(1000L, controller.getTypedState<AmountValue>("amount").value?.long)
        }

    // A symbol-first currency drawn by the field rather than smuggled into the label, which is what a
    // deployment has to do while the wire can only append.
    @Test
    fun `a currency prefix is drawn in front of the number`() =
        runFormsComposeUiTest {
            val controller =
                FormController(
                    FormSchema("form", fields = listOf(AmountFieldDefinition("amount", rules = emptyList()))),
                    initialValues = mapOf("amount" to AmountValue(1500L)),
                )

            setContent {
                TestKompotTheme {
                    AmountInputRenderer().Render(
                        component = AmountInputComponent(id = "c", fieldId = "amount", label = "Amount", currencyPrefix = "$"),
                        actionHandler = recordingActionHandler(),
                        formController = controller,
                    )
                }
            }

            onNodeWithText("$ 1 500").assertIsDisplayed()
        }

    // The side is the component's even when the symbol is not: a currency arriving in the value is a
    // string, so which field the component filled is what says where it goes.
    @Test
    fun `a currency derived from a neighbour keeps the side the component chose`() =
        runFormsComposeUiTest {
            val controller =
                FormController(
                    FormSchema(
                        "form",
                        fields =
                            listOf(
                                SelectionFieldDefinition("account"),
                                AmountFieldDefinition("amount", rules = emptyList()),
                            ),
                    ),
                    initialValues = mapOf("amount" to AmountValue(1500L)),
                )

            setContent {
                TestKompotTheme {
                    AmountInputRenderer().Render(
                        component =
                            AmountInputComponent(
                                id = "c",
                                fieldId = "amount",
                                label = "Amount",
                                currencyPrefix = "$",
                                currencyFromField = "account",
                            ),
                        actionHandler = recordingActionHandler(),
                        formController = controller,
                    )
                }
            }

            controller.onValueChanged("account", EntityValue(id = "acc_1", title = "Yen", rawMetadata = mapOf("currency" to "¥")))
            waitForIdle()

            onNodeWithText("¥ 1 500").assertIsDisplayed()
        }

    // The gap is one bit of how a currency is written, and the server holds the table that knows it.
    // Without this the same response draws "$ 50" in the field and "Between $10 and $50,000." in the
    // text under it — one currency, two ways, three lines apart.
    @Test
    fun `a currency the server marked unspaced is drawn against the number`() =
        runFormsComposeUiTest {
            val controller =
                FormController(
                    FormSchema("form", fields = listOf(AmountFieldDefinition("amount", rules = emptyList()))),
                    initialValues = mapOf("amount" to AmountValue(1500L)),
                )

            setContent {
                TestKompotTheme {
                    AmountInputRenderer().Render(
                        component =
                            AmountInputComponent(
                                id = "c",
                                fieldId = "amount",
                                label = "Amount",
                                currencyPrefix = "$",
                                currencySpaced = false,
                            ),
                        actionHandler = recordingActionHandler(),
                        formController = controller,
                    )
                }
            }

            onNodeWithText("$1 500").assertIsDisplayed()
        }

    // The control, and the compatibility question in one: a component that names only a suffix draws
    // exactly what it drew before.
    @Test
    fun `a suffix stays behind the number`() =
        runFormsComposeUiTest {
            val controller =
                FormController(
                    FormSchema("form", fields = listOf(AmountFieldDefinition("amount", rules = emptyList()))),
                    initialValues = mapOf("amount" to AmountValue(1500L)),
                )

            setContent {
                TestKompotTheme {
                    AmountInputRenderer().Render(
                        component = AmountInputComponent(id = "c", fieldId = "amount", label = "Amount", currencySuffix = "UZS"),
                        actionHandler = recordingActionHandler(),
                        formController = controller,
                    )
                }
            }

            onNodeWithText("1 500 UZS").assertIsDisplayed()
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
                                CheckboxFieldDefinition("show_amount"),
                                AmountFieldDefinition(
                                    "amount",
                                    rules = emptyList(),
                                    visibleIf = EqualsCondition("show_amount", BooleanValue(true)),
                                ),
                            ),
                    ),
                )

            setContent {
                TestKompotTheme {
                    AmountInputRenderer().Render(
                        component = AmountInputComponent(id = "c", fieldId = "amount", label = "Amount"),
                        actionHandler = recordingActionHandler(),
                        formController = controller,
                    )
                }
            }

            onAllNodesWithText("Amount").assertCountEquals(0)
        }

    @Test
    fun `losing focus on an empty required amount shows its validation error`() =
        runFormsComposeUiTest {
            val controller =
                FormController(
                    FormSchema(
                        "form",
                        fields =
                            listOf(
                                AmountFieldDefinition("amount", rules = listOf(RequiredRule("Enter an amount"))),
                                AmountFieldDefinition("other", rules = emptyList()),
                            ),
                    ),
                )

            setContent {
                TestKompotTheme {
                    Column {
                        AmountInputRenderer().Render(
                            component = AmountInputComponent(id = "c1", fieldId = "amount", label = "Amount"),
                            actionHandler = recordingActionHandler(),
                            formController = controller,
                        )
                        AmountInputRenderer().Render(
                            component = AmountInputComponent(id = "c2", fieldId = "other", label = "Other"),
                            actionHandler = recordingActionHandler(),
                            formController = controller,
                        )
                    }
                }
            }

            assertNull(controller.getTypedState<AmountValue>("amount").error)

            onNodeWithText("Amount").requestFocus()
            waitForIdle()
            onNodeWithText("Other").requestFocus()
            waitForIdle()

            assertEquals("Enter an amount", controller.getTypedState<AmountValue>("amount").error)
        }
}
