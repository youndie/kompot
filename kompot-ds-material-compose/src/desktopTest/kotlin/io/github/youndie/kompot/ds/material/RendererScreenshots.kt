package io.github.youndie.kompot.ds.material

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.remember
import io.github.youndie.kompot.AmountInputRenderer
import io.github.youndie.kompot.AutocompleteInputRenderer
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotActionHandler
import io.github.youndie.kompot.KompotRegistry
import io.github.youndie.kompot.ButtonRenderer
import io.github.youndie.kompot.CheckboxInputRenderer
import io.github.youndie.kompot.ColumnRenderer
import io.github.youndie.kompot.LocalKompotDesignSystem
import io.github.youndie.kompot.LocalKompotRegistry
import io.github.youndie.kompot.RadioGroupRenderer
import io.github.youndie.kompot.ReadOnlyFieldRenderer
import io.github.youndie.kompot.RowRenderer
import io.github.youndie.kompot.SelectInputRenderer
import io.github.youndie.kompot.TableRenderer
import io.github.youndie.kompot.TextInputRenderer
import io.github.youndie.kompot.TextRenderer
import io.github.youndie.kompot.TypographyToken
import io.github.youndie.kompot.material3.*
import io.github.youndie.kompot.kompotCoreRenderers
import io.github.youndie.kompot.kompotStandardRenderers
import io.github.youndie.kompot.generated.generatedFormsClientRenderers
import io.github.youndie.kompot.forms.AmountInputComponent
import io.github.youndie.kompot.forms.AutocompleteInputComponent
import io.github.youndie.kompot.forms.CheckboxInputComponent
import io.github.youndie.kompot.forms.KompotCheckboxVariants
import io.github.youndie.kompot.forms.ReadOnlyFieldComponent
import io.github.youndie.kompot.forms.RadioGroupComponent
import io.github.youndie.kompot.forms.SelectInputComponent
import io.github.youndie.kompot.forms.SelectOption
import io.github.youndie.kompot.forms.TextInputComponent
import io.github.youndie.kompot.standard.ButtonComponent
import io.github.youndie.kompot.standard.CloseAction
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.RowComponent
import io.github.youndie.kompot.standard.TableComponent
import io.github.youndie.kompot.standard.TableRow
import io.github.youndie.kompot.standard.TextComponent
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.form.FormSchema
import io.github.youndie.kompot.form.standard.AmountFieldDefinition
import io.github.youndie.kompot.form.standard.AmountValue
import io.github.youndie.kompot.form.standard.AutocompleteFieldDefinition
import io.github.youndie.kompot.form.standard.BooleanValue
import io.github.youndie.kompot.form.standard.CheckboxFieldDefinition
import io.github.youndie.kompot.form.standard.EntityValue
import io.github.youndie.kompot.form.standard.RequiredRule
import io.github.youndie.kompot.form.standard.SelectionFieldDefinition
import io.github.youndie.kompot.form.standard.TextFieldDefinition
import io.github.youndie.kompot.form.standard.TextValue
import ru.workinprogress.viddik.annotations.ViddikScreenshot
import ru.workinprogress.viddik.core.viddikTypography

// Text, Button and Column read no FormController, but the renderer interface asks for one uniformly.
// internal rather than private, because ComplexFormScreenshots.kt in this module uses it too and a
// top-level private is scoped to the FILE, not the package.
internal fun testFormController(): FormController = FormController(FormSchema(formId = "screenshot", fields = emptyList()))

internal fun recordingActionHandler(onAction: (KompotAction) -> Unit = {}) = KompotActionHandler { onAction(it) }

// Screenshots of several renderers under real conditions — with the actual Material3 design system,
// not the stand-in a behavioural test uses, which paints black in a default font on purpose so that
// those tests do not depend on a theme.
//
// This file lives in :kompot-ds-material-compose rather than in :kompot-client because the dependency
// runs that way: here is the only place where the real renderers and the real design system are both
// available.
internal val registry =
    KompotRegistry(kompotCoreRenderers + kompotStandardRenderers + generatedFormsClientRenderers)

@Composable
internal fun RendererScreenshotTheme(content: @Composable () -> Unit) {
    val locals: List<ProvidedValue<*>> =
        listOf(
            LocalKompotDesignSystem provides Material3DesignSystem(),
            LocalKompotRegistry provides registry,
        )
    // viddikTypography() is a requirement for portable goldens, not decoration. Glyph rasterisation is
    // normalised by the tester itself, but if the FONT differs there is nothing to normalise: without
    // an explicit typography Compose picks the system one — SF on macOS, DejaVu or Noto on Linux — and
    // the shots diverge by whole percents of pixels. The font travels inside the tester.
    //
    // Colours are left alone: MaterialTheme without a colorScheme gives the same default as before.
    MaterialTheme(typography = viddikTypography()) {
        CompositionLocalProvider(*locals.toTypedArray(), content = content)
    }
}

@ViddikScreenshot(name = "Text - title medium", group = "Renderer")
@Composable
fun TextRendererScreenshot() {
    RendererScreenshotTheme {
        TextRenderer().Render(
            component = TextComponent(id = "text", text = "Order summary", style = M3Typography.TitleMedium),
            actionHandler = recordingActionHandler(),
            formController = testFormController(),
        )
    }
}

@ViddikScreenshot(name = "Button - primary", group = "Renderer")
@Composable
fun ButtonRendererScreenshot() {
    RendererScreenshotTheme {
        ButtonRenderer().Render(
            component = ButtonComponent(id = "button", text = "Continue", action = CloseAction),
            actionHandler = recordingActionHandler(),
            formController = testFormController(),
        )
    }
}

@ViddikScreenshot(name = "TextInput - filled with error", group = "Renderer")
@Composable
fun TextInputRendererScreenshot() {
    val controller =
        remember {
            FormController(
                schema =
                    FormSchema(
                        formId = "screenshot",
                        fields =
                            listOf(
                                TextFieldDefinition(
                                    fieldId = "login",
                                    rules = listOf(RequiredRule("Enter your login")),
                                ),
                            ),
                    ),
                initialValues = mapOf("login" to TextValue("")),
            )
        }
    // markAllAsChanged forces a required field to show its error, so the shot catches the error
    // state as well.
    controller.markAllAsChanged()

    RendererScreenshotTheme {
        TextInputRenderer().Render(
            component = TextInputComponent(id = "input", fieldId = "login", label = "Login", placeholder = "admin"),
            actionHandler = recordingActionHandler(),
            formController = controller,
        )
    }
}

@ViddikScreenshot(name = "CheckboxInput - checked", group = "Renderer")
@Composable
fun CheckboxInputRendererScreenshot() {
    val controller =
        remember {
            FormController(
                schema = FormSchema(formId = "screenshot", fields = listOf(CheckboxFieldDefinition(fieldId = "agree"))),
                initialValues = mapOf("agree" to BooleanValue(true)),
            )
        }

    RendererScreenshotTheme {
        CheckboxInputRenderer().Render(
            component = CheckboxInputComponent(id = "checkbox", fieldId = "agree", label = "I accept the terms"),
            actionHandler = recordingActionHandler(),
            formController = controller,
        )
    }
}

// The same field, the same value, the other promise: a switch takes effect now and a checkbox takes
// effect on submit, which is what the variant exists to let a server say.
@ViddikScreenshot(name = "CheckboxInput - the switch variant", group = "Renderer")
@Composable
fun CheckboxInputSwitchVariantScreenshot() {
    val controller =
        remember {
            FormController(
                schema = FormSchema(formId = "screenshot", fields = listOf(CheckboxFieldDefinition(fieldId = "roaming"))),
                initialValues = mapOf("roaming" to BooleanValue(true)),
            )
        }

    RendererScreenshotTheme {
        CheckboxInputRenderer().Render(
            component =
                CheckboxInputComponent(
                    id = "checkbox",
                    fieldId = "roaming",
                    label = "Roaming",
                    variant = KompotCheckboxVariants.SWITCH,
                ),
            actionHandler = recordingActionHandler(),
            formController = controller,
        )
    }
}

@ViddikScreenshot(name = "Column - text and button", group = "Renderer")
@Composable
fun ColumnRendererScreenshot() {
    RendererScreenshotTheme {
        ColumnRenderer().Render(
            component =
                ColumnComponent(
                    id = "column",
                    spacing = 12,
                    children =
                        listOf(
                            TextComponent(
                                id = "column_text",
                                text = "Order summary",
                                style = M3Typography.TitleMedium,
                            ),
                            ButtonComponent(id = "column_button", text = "Continue", action = CloseAction),
                        ),
                ),
            actionHandler = recordingActionHandler(),
            formController = testFormController(),
        )
    }
}

@ViddikScreenshot(name = "TextInput - filled, no error", group = "Renderer")
@Composable
fun TextInputRendererFilledScreenshot() {
    val controller =
        remember {
            FormController(
                schema = FormSchema(formId = "screenshot", fields = listOf(TextFieldDefinition(fieldId = "login", rules = emptyList()))),
                initialValues = mapOf("login" to TextValue("admin")),
            )
        }

    RendererScreenshotTheme {
        TextInputRenderer().Render(
            component = TextInputComponent(id = "input", fieldId = "login", label = "Login", placeholder = "admin"),
            actionHandler = recordingActionHandler(),
            formController = controller,
        )
    }
}

// secret = true masks a password visually, and until this shot nothing pinned that down.
@ViddikScreenshot(name = "TextInput - secret (password masking)", group = "Renderer")
@Composable
fun TextInputRendererSecretScreenshot() {
    val controller =
        remember {
            FormController(
                schema =
                    FormSchema(formId = "screenshot", fields = listOf(TextFieldDefinition(fieldId = "password", rules = emptyList()))),
                initialValues = mapOf("password" to TextValue("hunter2")),
            )
        }

    RendererScreenshotTheme {
        TextInputRenderer().Render(
            component = TextInputComponent(id = "input", fieldId = "password", label = "Password", secret = true),
            actionHandler = recordingActionHandler(),
            formController = controller,
        )
    }
}

@ViddikScreenshot(name = "AmountInput - filled", group = "Renderer")
@Composable
fun AmountInputRendererFilledScreenshot() {
    val controller =
        remember {
            FormController(
                schema = FormSchema(formId = "screenshot", fields = listOf(AmountFieldDefinition(fieldId = "amount", rules = emptyList()))),
                initialValues = mapOf("amount" to AmountValue(150_000L)),
            )
        }

    RendererScreenshotTheme {
        AmountInputRenderer().Render(
            component = AmountInputComponent(id = "amount_input", fieldId = "amount", label = "Amount", currencySuffix = "EUR"),
            actionHandler = recordingActionHandler(),
            formController = controller,
        )
    }
}

@ViddikScreenshot(name = "AmountInput - required error", group = "Renderer")
@Composable
fun AmountInputRendererErrorScreenshot() {
    val controller =
        remember {
            FormController(
                schema =
                    FormSchema(
                        formId = "screenshot",
                        fields = listOf(AmountFieldDefinition(fieldId = "amount", rules = listOf(RequiredRule("Enter an amount")))),
                    ),
            )
        }
    controller.markAllAsChanged()

    RendererScreenshotTheme {
        AmountInputRenderer().Render(
            component = AmountInputComponent(id = "amount_input", fieldId = "amount", label = "Amount", currencySuffix = "EUR"),
            actionHandler = recordingActionHandler(),
            formController = controller,
        )
    }
}

// No dataSourceResolver: on the first composition the autocomplete never calls searchOptions —
// skipNextSearch starts true — and the dropdown opens only on user input, so a static "already
// selected" shot needs no fake resolver.
@ViddikScreenshot(name = "AutocompleteInput - selected entity", group = "Renderer")
@Composable
fun AutocompleteInputRendererScreenshot() {
    val controller =
        remember {
            FormController(
                schema =
                    FormSchema(
                        formId = "screenshot",
                        fields = listOf(AutocompleteFieldDefinition(fieldId = "beneficiary", dataSourceId = "search")),
                    ),
                initialValues = mapOf("beneficiary" to EntityValue(id = "b1", title = "Ada Lovelace")),
            )
        }

    RendererScreenshotTheme {
        AutocompleteInputRenderer().Render(
            component =
                AutocompleteInputComponent(id = "autocomplete_input", fieldId = "beneficiary", label = "Recipient", dataSourceId = "search"),
            actionHandler = recordingActionHandler(),
            formController = controller,
        )
    }
}

private val currencyOptions =
    listOf(
        SelectOption(id = "usd", label = "US dollar", rawMetadata = mapOf("currency" to "USD")),
        SelectOption(id = "uzs", label = "Euro"),
    )

@ViddikScreenshot(name = "SelectInput - selected option", group = "Renderer")
@Composable
fun SelectInputRendererScreenshot() {
    val controller =
        remember {
            FormController(
                schema = FormSchema(formId = "screenshot", fields = listOf(SelectionFieldDefinition(fieldId = "currency"))),
                initialValues = mapOf("currency" to EntityValue(id = "usd", title = "US dollar")),
            )
        }

    RendererScreenshotTheme {
        SelectInputRenderer().Render(
            component = SelectInputComponent(id = "select_input", fieldId = "currency", label = "Currency", options = currencyOptions),
            actionHandler = recordingActionHandler(),
            formController = controller,
        )
    }
}

@ViddikScreenshot(name = "SelectInput - required error", group = "Renderer")
@Composable
fun SelectInputRendererErrorScreenshot() {
    val controller =
        remember {
            FormController(
                schema =
                    FormSchema(
                        formId = "screenshot",
                        fields = listOf(SelectionFieldDefinition(fieldId = "currency", rules = listOf(RequiredRule("Choose a currency")))),
                    ),
            )
        }
    controller.markAllAsChanged()

    RendererScreenshotTheme {
        SelectInputRenderer().Render(
            component = SelectInputComponent(id = "select_input", fieldId = "currency", label = "Currency", options = currencyOptions),
            actionHandler = recordingActionHandler(),
            formController = controller,
        )
    }
}

private val payerTypeOptions =
    listOf(
        SelectOption(id = "individual", label = "Individual"),
        SelectOption(id = "business", label = "Company", rawMetadata = mapOf("kind" to "b2b")),
    )

@ViddikScreenshot(name = "RadioGroup - selected option", group = "Renderer")
@Composable
fun RadioGroupRendererScreenshot() {
    val controller =
        remember {
            FormController(
                schema = FormSchema(formId = "screenshot", fields = listOf(SelectionFieldDefinition(fieldId = "payer_type"))),
                initialValues = mapOf("payer_type" to EntityValue(id = "individual", title = "Individual")),
            )
        }

    RendererScreenshotTheme {
        RadioGroupRenderer().Render(
            component = RadioGroupComponent(id = "radio_group", fieldId = "payer_type", label = "Customer type", options = payerTypeOptions),
            actionHandler = recordingActionHandler(),
            formController = controller,
        )
    }
}

@ViddikScreenshot(name = "RadioGroup - required error", group = "Renderer")
@Composable
fun RadioGroupRendererErrorScreenshot() {
    val controller =
        remember {
            FormController(
                schema =
                    FormSchema(
                        formId = "screenshot",
                        fields =
                            listOf(
                                SelectionFieldDefinition(fieldId = "payer_type", rules = listOf(RequiredRule("Choose a customer type"))),
                            ),
                    ),
            )
        }
    controller.markAllAsChanged()

    RendererScreenshotTheme {
        RadioGroupRenderer().Render(
            component = RadioGroupComponent(id = "radio_group", fieldId = "payer_type", label = "Customer type", options = payerTypeOptions),
            actionHandler = recordingActionHandler(),
            formController = controller,
        )
    }
}

@ViddikScreenshot(name = "ReadOnlyField - with helper text", group = "Renderer")
@Composable
fun ReadOnlyFieldRendererScreenshot() {
    RendererScreenshotTheme {
        ReadOnlyFieldRenderer().Render(
            component =
                ReadOnlyFieldComponent(id = "readonly", label = "Sender", value = "Ada Lovelace", helperText = "From your profile"),
            actionHandler = recordingActionHandler(),
            formController = testFormController(),
        )
    }
}

@ViddikScreenshot(name = "Row - two texts side by side", group = "Renderer")
@Composable
fun RowRendererScreenshot() {
    RendererScreenshotTheme {
        RowRenderer().Render(
            component =
                RowComponent(
                    id = "row",
                    spacing = 12,
                    children =
                        listOf(
                            TextComponent(id = "row_text_1", text = "Document no.", style = M3Typography.BodyMedium),
                            TextComponent(id = "row_text_2", text = "Document date", style = M3Typography.BodyMedium),
                        ),
                ),
            actionHandler = recordingActionHandler(),
            formController = testFormController(),
        )
    }
}

@ViddikScreenshot(name = "Table - header and rows", group = "Renderer")
@Composable
fun TableRendererScreenshot() {
    RendererScreenshotTheme {
        TableRenderer().Render(
            component =
                TableComponent(
                    id = "table",
                    rows =
                        listOf(
                            TableRow(listOf("Order type", "Standard discount", "Promo discount"), header = true),
                            TableRow(listOf("From a template", "1%", "10%")),
                            TableRow(listOf("Custom", "1%", "3%")),
                        ),
                ),
            actionHandler = recordingActionHandler(),
            formController = testFormController(),
        )
    }
}
