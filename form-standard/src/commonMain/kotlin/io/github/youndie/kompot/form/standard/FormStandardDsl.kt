package io.github.youndie.kompot.form.standard

import io.github.youndie.kompot.form.FieldValue
import io.github.youndie.kompot.form.FormCondition
import io.github.youndie.kompot.form.FormSchemaBuilder
import io.github.youndie.kompot.form.ValidationRulesBuilder

public fun FormSchemaBuilder.textField(
    fieldId: String,
    keyboardType: KeyboardType = KeyboardType.TEXT,
    mask: String? = null,
    visibleIf: FormCondition? = null,
    triggersPatch: Boolean = false,
    rules: ValidationRulesBuilder.() -> Unit = {},
) {
    field(
        TextFieldDefinition(
            fieldId = fieldId,
            rules = ValidationRulesBuilder().apply(rules).build(),
            keyboardType = keyboardType,
            mask = mask,
            visibleIf = visibleIf,
            triggersPatch = triggersPatch,
        ),
    )
}

// No currency is set here: it is carried by the value itself (AmountValue.currency) and by the UI
// component (amount_input.currencySuffix), and those are what renderers read. The third copy on the
// field definition was write-only — nothing read it, while its default belonged to one particular
// application.
public fun FormSchemaBuilder.amountField(
    fieldId: String,
    visibleIf: FormCondition? = null,
    triggersPatch: Boolean = false,
    rules: ValidationRulesBuilder.() -> Unit = {},
) {
    field(
        AmountFieldDefinition(
            fieldId = fieldId,
            rules = ValidationRulesBuilder().apply(rules).build(),
            visibleIf = visibleIf,
            triggersPatch = triggersPatch,
        ),
    )
}

public fun FormSchemaBuilder.checkboxField(
    fieldId: String,
    visibleIf: FormCondition? = null,
    triggersPatch: Boolean = false,
    rules: ValidationRulesBuilder.() -> Unit = {},
) {
    field(
        CheckboxFieldDefinition(
            fieldId = fieldId,
            rules = ValidationRulesBuilder().apply(rules).build(),
            visibleIf = visibleIf,
            triggersPatch = triggersPatch,
        ),
    )
}

public fun FormSchemaBuilder.selectionField(
    fieldId: String,
    visibleIf: FormCondition? = null,
    triggersPatch: Boolean = false,
    rules: ValidationRulesBuilder.() -> Unit = {},
) {
    field(
        SelectionFieldDefinition(
            fieldId = fieldId,
            rules = ValidationRulesBuilder().apply(rules).build(),
            visibleIf = visibleIf,
            triggersPatch = triggersPatch,
        ),
    )
}

public fun FormSchemaBuilder.autocompleteField(
    fieldId: String,
    dataSourceId: String,
    visibleIf: FormCondition? = null,
    triggersPatch: Boolean = false,
    rules: ValidationRulesBuilder.() -> Unit = {},
) {
    field(
        AutocompleteFieldDefinition(
            fieldId = fieldId,
            rules = ValidationRulesBuilder().apply(rules).build(),
            dataSourceId = dataSourceId,
            visibleIf = visibleIf,
            triggersPatch = triggersPatch,
        ),
    )
}

// A convenience constructor for a visibleIf condition:
//   textField("gift_message", visibleIf = equals("is_gift", BooleanValue(true)))
public fun equals(
    fieldId: String,
    expectedValue: FieldValue,
): FormCondition = EqualsCondition(fieldId, expectedValue)

// Holds while a field is not filled in either (null != expectedValue):
//   textField("doc_number", visibleIf = notEquals("auto_numbering", BooleanValue(true)))
public fun notEquals(
    fieldId: String,
    expectedValue: FieldValue,
): FormCondition = NotEqualsCondition(fieldId, expectedValue)

public fun ValidationRulesBuilder.required(errorMessage: String) {
    rule(RequiredRule(errorMessage))
}

public fun ValidationRulesBuilder.regex(
    pattern: String,
    errorMessage: String,
) {
    rule(RegexRule(pattern, errorMessage))
}

// Cross-field validation:
//   textField("gift_message") { requiredIf("is_gift", BooleanValue(true), "Write a message") }
public fun ValidationRulesBuilder.requiredIf(
    targetFieldId: String,
    expectedValue: FieldValue,
    errorMessage: String,
) {
    rule(RequiredIfRule(targetFieldId, expectedValue, errorMessage))
}

// A UX pre-check: the amount must not exceed what is left on the selected entity. The entity arrives
// as an EntityValue, and the remaining amount sits in its rawMetadata under a key the protocol
// reserves (see SPEC.md §9.7):
//   amountField("amount") { maxAmountFromField("source", errorMessage = "Not enough left") }
public fun ValidationRulesBuilder.maxAmountFromField(
    balanceFieldId: String,
    balanceMetadataKey: String = "balance",
    errorMessage: String,
) {
    rule(MaxAmountRule(balanceFieldId, balanceMetadataKey, errorMessage))
}
