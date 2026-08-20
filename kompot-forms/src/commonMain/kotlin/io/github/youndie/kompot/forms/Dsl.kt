package io.github.youndie.kompot.forms

import io.github.youndie.kompot.dsl.KompotContainerContext
import io.github.youndie.kompot.dsl.KompotModifierBuilder
import kotlin.uuid.Uuid

fun KompotContainerContext.textInput(
    fieldId: String,
    label: String,
    placeholder: String? = null,
    mask: String? = null,
    uppercase: Boolean = false,
    secret: Boolean = false,
    id: String? = null,
    modifierBlock: (KompotModifierBuilder.() -> Unit)? = null,
) {
    val mods = modifierBlock?.let { KompotModifierBuilder().apply(it).build() } ?: emptyList()
    addComponent(
        TextInputComponent(
            id = id ?: Uuid.random().toString(),
            modifiers = mods,
            fieldId = fieldId,
            label = label,
            placeholder = placeholder,
            mask = mask,
            uppercase = uppercase,
            secret = secret,
        ),
    )
}

fun KompotContainerContext.amountInput(
    fieldId: String,
    label: String,
    currencySuffix: String? = null,
    currencyFromField: String? = null,
    id: String? = null,
    modifierBlock: (KompotModifierBuilder.() -> Unit)? = null,
) {
    val mods = modifierBlock?.let { KompotModifierBuilder().apply(it).build() } ?: emptyList()
    addComponent(
        AmountInputComponent(
            id = id ?: Uuid.random().toString(),
            modifiers = mods,
            fieldId = fieldId,
            label = label,
            currencySuffix = currencySuffix,
            currencyFromField = currencyFromField,
        ),
    )
}

fun KompotContainerContext.readOnlyField(
    label: String,
    value: String,
    helperText: String? = null,
    id: String? = null,
    modifierBlock: (KompotModifierBuilder.() -> Unit)? = null,
) {
    val mods = modifierBlock?.let { KompotModifierBuilder().apply(it).build() } ?: emptyList()
    addComponent(
        ReadOnlyFieldComponent(
            id = id ?: Uuid.random().toString(),
            modifiers = mods,
            label = label,
            value = value,
            helperText = helperText,
        ),
    )
}

fun KompotContainerContext.checkboxInput(
    fieldId: String,
    label: String,
    id: String? = null,
    modifierBlock: (KompotModifierBuilder.() -> Unit)? = null,
) {
    val mods = modifierBlock?.let { KompotModifierBuilder().apply(it).build() } ?: emptyList()
    addComponent(
        CheckboxInputComponent(
            id = id ?: Uuid.random().toString(),
            modifiers = mods,
            fieldId = fieldId,
            label = label,
        ),
    )
}

fun KompotContainerContext.autocompleteInput(
    fieldId: String,
    label: String,
    dataSourceId: String,
    placeholder: String? = null,
    id: String? = null,
    modifierBlock: (KompotModifierBuilder.() -> Unit)? = null,
) {
    val mods = modifierBlock?.let { KompotModifierBuilder().apply(it).build() } ?: emptyList()
    addComponent(
        AutocompleteInputComponent(
            id = id ?: Uuid.random().toString(),
            modifiers = mods,
            fieldId = fieldId,
            label = label,
            dataSourceId = dataSourceId,
            placeholder = placeholder,
        ),
    )
}

fun KompotContainerContext.selectInput(
    fieldId: String,
    label: String,
    options: List<SelectOption>,
    placeholder: String? = null,
    id: String? = null,
    modifierBlock: (KompotModifierBuilder.() -> Unit)? = null,
) {
    val mods = modifierBlock?.let { KompotModifierBuilder().apply(it).build() } ?: emptyList()
    addComponent(
        SelectInputComponent(
            id = id ?: Uuid.random().toString(),
            modifiers = mods,
            fieldId = fieldId,
            label = label,
            options = options,
            placeholder = placeholder,
        ),
    )
}

fun KompotContainerContext.radioGroup(
    fieldId: String,
    label: String,
    options: List<SelectOption>,
    id: String? = null,
    modifierBlock: (KompotModifierBuilder.() -> Unit)? = null,
) {
    val mods = modifierBlock?.let { KompotModifierBuilder().apply(it).build() } ?: emptyList()
    addComponent(
        RadioGroupComponent(
            id = id ?: Uuid.random().toString(),
            modifiers = mods,
            fieldId = fieldId,
            label = label,
            options = options,
        ),
    )
}
