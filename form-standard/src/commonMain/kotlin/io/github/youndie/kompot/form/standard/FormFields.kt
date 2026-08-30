package io.github.youndie.kompot.form.standard

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import io.github.youndie.kompot.form.FieldValue
import io.github.youndie.kompot.form.FormCondition
import io.github.youndie.kompot.form.FormFieldDefinition
import io.github.youndie.kompot.form.ValidationRule

public enum class KeyboardType {
    TEXT,
    NUMBER,
    EMAIL,
    PHONE,
}

@Serializable
@SerialName("text_field")
public data class TextFieldDefinition(
    override val fieldId: String,
    override val rules: List<@Polymorphic ValidationRule>,
    val keyboardType: KeyboardType = KeyboardType.TEXT,
    // A visual input mask, "+1 (###) ###-##-##" for instance. "#" is a digit placeholder, every other
    // character is a literal. The stored value stays raw, without the mask.
    val mask: String? = null,
    override val visibleIf: @Polymorphic FormCondition? = null,
    override val triggersPatch: Boolean = false,
    override val initialValue: @Polymorphic FieldValue? = null,
) : FormFieldDefinition

@Serializable
@SerialName("amount_field")
// There is deliberately no currency on the field definition. There used to be one, defaulting to a
// particular application's currency, and nothing read it: renderers take the currency from the value
// itself (AmountValue.currency) or from the UI component (amount_input.currencySuffix). Checked
// across every consumer — Compose, SwiftUI, the reference corpus — a third home for the same fact
// only let you set it and see no effect.
public data class AmountFieldDefinition(
    override val fieldId: String,
    override val rules: List<@Polymorphic ValidationRule>,
    override val visibleIf: @Polymorphic FormCondition? = null,
    override val triggersPatch: Boolean = false,
    override val initialValue: @Polymorphic FieldValue? = null,
) : FormFieldDefinition

@Serializable
@SerialName("checkbox_field")
public data class CheckboxFieldDefinition(
    override val fieldId: String,
    override val rules: List<@Polymorphic ValidationRule> = emptyList(),
    override val visibleIf: @Polymorphic FormCondition? = null,
    override val triggersPatch: Boolean = false,
    override val initialValue: @Polymorphic FieldValue? = null,
) : FormFieldDefinition

// A field with remote search. dataSourceId is the identifier of a resource on the backend, not a
// URL: resolving it into an address is the client's business (see RemoteDataSourceResolver in
// :form-core), and the UI layer knows nothing about the network.
@Serializable
@SerialName("autocomplete_field")
public data class AutocompleteFieldDefinition(
    override val fieldId: String,
    override val rules: List<@Polymorphic ValidationRule> = emptyList(),
    val dataSourceId: String,
    override val visibleIf: @Polymorphic FormCondition? = null,
    override val triggersPatch: Boolean = false,
    override val initialValue: @Polymorphic FieldValue? = null,
) : FormFieldDefinition

// A choice from a fixed list — a dropdown or a radio group. The options themselves are a matter of
// presentation and live in the UI component (:kompot-forms); the schema describes the data contract
// only. The value of such a field is an EntityValue(id, title).
@Serializable
@SerialName("selection_field")
public data class SelectionFieldDefinition(
    override val fieldId: String,
    override val rules: List<@Polymorphic ValidationRule> = emptyList(),
    override val visibleIf: @Polymorphic FormCondition? = null,
    override val triggersPatch: Boolean = false,
    override val initialValue: @Polymorphic FieldValue? = null,
) : FormFieldDefinition
