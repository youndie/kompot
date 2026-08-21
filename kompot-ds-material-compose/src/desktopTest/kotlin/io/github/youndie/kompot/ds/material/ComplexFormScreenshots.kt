package io.github.youndie.kompot.ds.material

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.youndie.kompot.LocalKompotRegistry
import io.github.youndie.kompot.forms.SelectOption
import io.github.youndie.kompot.forms.SubmitFormAction
import io.github.youndie.kompot.forms.standard.boundAmountInput
import io.github.youndie.kompot.forms.standard.boundAutocompleteInput
import io.github.youndie.kompot.forms.standard.boundCheckboxInput
import io.github.youndie.kompot.forms.standard.boundRadioGroup
import io.github.youndie.kompot.forms.standard.boundSelectInput
import io.github.youndie.kompot.forms.standard.boundTextInput
import io.github.youndie.kompot.forms.standard.buildFormScreen
import io.github.youndie.kompot.forms.standard.row
import io.github.youndie.kompot.material3.*
import io.github.youndie.kompot.standard.button
import io.github.youndie.kompot.standard.text
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.form.standard.AmountValue
import io.github.youndie.kompot.form.standard.EntityValue
import io.github.youndie.kompot.form.standard.KeyboardType
import io.github.youndie.kompot.form.standard.TextValue
import io.github.youndie.kompot.form.standard.required
import ru.workinprogress.viddik.annotations.ViddikScreenshot

// Unlike RendererScreenshots.kt, which photographs one renderer at a time, these shots assemble a
// WHOLE form through buildFormScreen{} — the same DSL a real server writes its schemas with — and
// render it through the registry rather than a single renderer. So a shot catches not only "what one
// field looks like" but the real composition: the spacing between fields, a nested row, the order of
// validation errors after markAllAsChanged(). None of that is visible in a shot of one renderer.
//
// The capture window is deliberately larger than the default: a multi-field form is taller than a
// single renderer and would be cropped.

@ViddikScreenshot(name = "Checkout form - filled, ready to submit", group = "ComplexForm", width = 400, height = 480)
@Composable
fun P2pTransferFormFilledScreenshot() {
    val recipients = listOf(SelectOption(id = "user2", label = "user2"))
    val response =
        buildFormScreen("checkout") {
            spacing(20)
            modifier {
                padding(top = 16, bottom = 32, start = 16, end = 16)
                background(M3Colors.Background)
            }
            text(id = "balance", text = "In stock: 12 items", style = M3Typography.TitleMedium)
            boundSelectInput(fieldId = "recipient", label = "Recipient", options = recipients) {
                required("Choose a recipient")
            }
            boundAmountInput(fieldId = "amount", label = "Amount") {
                required("Enter an amount")
            }
            boundTextInput(
                fieldId = "otp",
                label = "Confirmation code",
                placeholder = "Demo code: 1234",
                keyboardType = KeyboardType.NUMBER,
            ) {
                required("Enter the confirmation code")
            }
            button(text = "Place order", action = SubmitFormAction(formId = "checkout"), modifierBlock = { fillMaxWidth() })
        }

    val controller =
        remember {
            FormController(
                schema = response.schema,
                initialValues =
                    mapOf(
                        "recipient" to EntityValue(id = "user2", title = "user2"),
                        "amount" to AmountValue(100L),
                        "otp" to TextValue("1234"),
                    ),
            )
        }

    RendererScreenshotTheme {
        LocalKompotRegistry.current.RenderNode(response.screen, recordingActionHandler(), controller)
    }
}

// An empty form plus markAllAsChanged(): all three required errors at once, in a real composition
// rather than in isolation, so the shot catches the vertical spacing between a field and its error
// text and the fact that the neighbouring fields do not shift.
@ViddikScreenshot(name = "Checkout form - empty, all required errors", group = "ComplexForm", width = 400, height = 520)
@Composable
fun P2pTransferFormErrorsScreenshot() {
    val recipients = listOf(SelectOption(id = "user2", label = "user2"))
    val response =
        buildFormScreen("checkout") {
            spacing(20)
            modifier {
                padding(top = 16, bottom = 32, start = 16, end = 16)
                background(M3Colors.Background)
            }
            text(id = "balance", text = "In stock: 12 items", style = M3Typography.TitleMedium)
            boundSelectInput(fieldId = "recipient", label = "Recipient", options = recipients) {
                required("Choose a recipient")
            }
            boundAmountInput(fieldId = "amount", label = "Amount") {
                required("Enter an amount")
            }
            boundTextInput(
                fieldId = "otp",
                label = "Confirmation code",
                placeholder = "Demo code: 1234",
                keyboardType = KeyboardType.NUMBER,
            ) {
                required("Enter the confirmation code")
            }
            button(text = "Place order", action = SubmitFormAction(formId = "checkout"), modifierBlock = { fillMaxWidth() })
        }

    val controller = remember { FormController(schema = response.schema) }
    controller.markAllAsChanged()

    RendererScreenshotTheme {
        LocalKompotRegistry.current.RenderNode(response.screen, recordingActionHandler(), controller)
    }
}

// A more heterogeneous form: text, amount, autocomplete, checkbox, radio group and a nested row with
// two fields side by side. It shows that a row inside buildFormScreen composes fields side by side
// instead of breaking the form — which the row shot in RendererScreenshots.kt cannot show, since it
// holds text only.
@ViddikScreenshot(name = "Template form - mixed field types, filled", group = "ComplexForm", width = 400, height = 680)
@Composable
fun TemplateFormFilledScreenshot() {
    val response =
        buildFormScreen("payment_template") {
            spacing(16)
            modifier {
                padding(top = 16, bottom = 32, start = 16, end = 16)
                background(M3Colors.Background)
            }
            text(id = "title", text = "New order template", style = M3Typography.TitleMedium)
            boundTextInput(fieldId = "template_name", label = "Template name") {
                required("Enter a name")
            }
            boundAutocompleteInput(fieldId = "beneficiary", label = "Recipient", dataSourceId = "beneficiaries") {
                required("Choose a recipient")
            }
            row {
                spacing(12)
                boundAmountInput(fieldId = "amount", label = "Amount", modifierBlock = { weight(1f) }) {
                    required("Enter an amount")
                }
                boundTextInput(fieldId = "purpose_code", label = "Purpose code", modifierBlock = { weight(1f) })
            }
            boundCheckboxInput(fieldId = "save_as_favorite", label = "Add to favourites")
            boundRadioGroup(
                fieldId = "schedule",
                label = "Repeat",
                options =
                    listOf(
                        SelectOption(id = "once", label = "One-off"),
                        SelectOption(id = "monthly", label = "Monthly"),
                    ),
            )
            button(text = "Save as a template", action = SubmitFormAction(formId = "payment_template"), modifierBlock = { fillMaxWidth() })
        }

    val controller =
        remember {
            FormController(
                schema = response.schema,
                initialValues =
                    mapOf(
                        "template_name" to TextValue("Monthly plan"),
                        "beneficiary" to EntityValue(id = "b1", title = "Acme Ltd"),
                        "amount" to AmountValue(500_000L),
                        "purpose_code" to TextValue("412"),
                        "schedule" to EntityValue(id = "monthly", title = "Monthly"),
                    ),
            )
        }

    RendererScreenshotTheme {
        LocalKompotRegistry.current.RenderNode(response.screen, recordingActionHandler(), controller)
    }
}
