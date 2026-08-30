package io.github.youndie.kompot.forms.standard

import io.github.youndie.kompot.dsl.KompotModifierBuilder
import io.github.youndie.kompot.forms.SelectOption
import io.github.youndie.kompot.forms.amountInput
import io.github.youndie.kompot.forms.autocompleteInput
import io.github.youndie.kompot.forms.checkboxInput
import io.github.youndie.kompot.forms.radioGroup
import io.github.youndie.kompot.forms.selectInput
import io.github.youndie.kompot.forms.textInput
import io.github.youndie.kompot.form.FormCondition
import io.github.youndie.kompot.form.ValidationRulesBuilder
import io.github.youndie.kompot.form.standard.AmountFieldDefinition
import io.github.youndie.kompot.form.standard.AutocompleteFieldDefinition
import io.github.youndie.kompot.form.standard.CheckboxFieldDefinition
import io.github.youndie.kompot.form.standard.KeyboardType
import io.github.youndie.kompot.form.standard.SelectionFieldDefinition
import io.github.youndie.kompot.form.standard.TextFieldDefinition

// Bound fields: one call both draws the component AND adds its definition to the form schema, so a
// fieldId is declared once and the UI cannot drift from the schema.
//
// For free-standing UI — text, a column, a button, a read-only field — use the ordinary builders of
// kompot-standard and kompot-forms; the form builder is a KompotContainerContext itself.

public fun KompotFormContext.boundTextInput(
    fieldId: String,
    label: String,
    placeholder: String? = null,
    mask: String? = null,
    uppercase: Boolean = false,
    secret: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.TEXT,
    visibleIf: FormCondition? = null,
    triggersPatch: Boolean = false,
    id: String? = null,
    modifierBlock: (KompotModifierBuilder.() -> Unit)? = null,
    rules: ValidationRulesBuilder.() -> Unit = {},
) {
    textInput(
        fieldId = fieldId,
        label = label,
        placeholder = placeholder,
        mask = mask,
        uppercase = uppercase,
        secret = secret,
        id = id,
        modifierBlock = modifierBlock,
    )
    field(
        TextFieldDefinition(
            fieldId = fieldId,
            rules = buildRules(rules),
            keyboardType = keyboardType,
            mask = mask,
            visibleIf = visibleIf,
            triggersPatch = triggersPatch,
        ),
    )
}

// The currency is a matter of UI only: the three currency parameters say how it is written — the
// symbol, which side of the number it goes on, and whether it stands away from it — and
// currencyFromField says which neighbouring field to take the symbol from. The field definition has
// no currency of its own any more — nothing read it, and its default belonged to one application.
public fun KompotFormContext.boundAmountInput(
    fieldId: String,
    label: String,
    currencySuffix: String? = null,
    currencyPrefix: String? = null,
    currencySpaced: Boolean = true,
    currencyFromField: String? = null,
    visibleIf: FormCondition? = null,
    triggersPatch: Boolean = false,
    id: String? = null,
    modifierBlock: (KompotModifierBuilder.() -> Unit)? = null,
    rules: ValidationRulesBuilder.() -> Unit = {},
) {
    amountInput(
        fieldId = fieldId,
        label = label,
        currencySuffix = currencySuffix,
        currencyPrefix = currencyPrefix,
        currencySpaced = currencySpaced,
        currencyFromField = currencyFromField,
        id = id,
        modifierBlock = modifierBlock,
    )
    field(
        AmountFieldDefinition(
            fieldId = fieldId,
            rules = buildRules(rules),
            visibleIf = visibleIf,
            triggersPatch = triggersPatch,
        ),
    )
}

public fun KompotFormContext.boundCheckboxInput(
    fieldId: String,
    label: String,
    visibleIf: FormCondition? = null,
    triggersPatch: Boolean = false,
    id: String? = null,
    modifierBlock: (KompotModifierBuilder.() -> Unit)? = null,
    rules: ValidationRulesBuilder.() -> Unit = {},
) {
    checkboxInput(fieldId = fieldId, label = label, id = id, modifierBlock = modifierBlock)
    field(
        CheckboxFieldDefinition(
            fieldId = fieldId,
            rules = buildRules(rules),
            visibleIf = visibleIf,
            triggersPatch = triggersPatch,
        ),
    )
}

public fun KompotFormContext.boundAutocompleteInput(
    fieldId: String,
    label: String,
    dataSourceId: String,
    placeholder: String? = null,
    visibleIf: FormCondition? = null,
    triggersPatch: Boolean = false,
    id: String? = null,
    modifierBlock: (KompotModifierBuilder.() -> Unit)? = null,
    rules: ValidationRulesBuilder.() -> Unit = {},
) {
    autocompleteInput(
        fieldId = fieldId,
        label = label,
        dataSourceId = dataSourceId,
        placeholder = placeholder,
        id = id,
        modifierBlock = modifierBlock,
    )
    field(
        AutocompleteFieldDefinition(
            fieldId = fieldId,
            rules = buildRules(rules),
            dataSourceId = dataSourceId,
            visibleIf = visibleIf,
            triggersPatch = triggersPatch,
        ),
    )
}

public fun KompotFormContext.boundSelectInput(
    fieldId: String,
    label: String,
    options: List<SelectOption>,
    placeholder: String? = null,
    visibleIf: FormCondition? = null,
    triggersPatch: Boolean = false,
    id: String? = null,
    modifierBlock: (KompotModifierBuilder.() -> Unit)? = null,
    rules: ValidationRulesBuilder.() -> Unit = {},
) {
    selectInput(
        fieldId = fieldId,
        label = label,
        options = options,
        placeholder = placeholder,
        id = id,
        modifierBlock = modifierBlock,
    )
    field(
        SelectionFieldDefinition(
            fieldId = fieldId,
            rules = buildRules(rules),
            visibleIf = visibleIf,
            triggersPatch = triggersPatch,
        ),
    )
}

public fun KompotFormContext.boundRadioGroup(
    fieldId: String,
    label: String,
    options: List<SelectOption>,
    visibleIf: FormCondition? = null,
    triggersPatch: Boolean = false,
    id: String? = null,
    modifierBlock: (KompotModifierBuilder.() -> Unit)? = null,
    rules: ValidationRulesBuilder.() -> Unit = {},
) {
    radioGroup(fieldId = fieldId, label = label, options = options, id = id, modifierBlock = modifierBlock)
    field(
        SelectionFieldDefinition(
            fieldId = fieldId,
            rules = buildRules(rules),
            visibleIf = visibleIf,
            triggersPatch = triggersPatch,
        ),
    )
}
