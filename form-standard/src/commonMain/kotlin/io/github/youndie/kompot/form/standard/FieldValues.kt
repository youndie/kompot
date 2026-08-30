package io.github.youndie.kompot.form.standard

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import io.github.youndie.kompot.form.FieldValue

@Serializable
@SerialName("text_value")
public data class TextValue(
    val text: String,
) : FieldValue {
    override val plainValue: String get() = text
}

@Serializable
@SerialName("amount_value")
public data class AmountValue(
    val long: Long,
    // The currency of the value itself, which may depend on what is selected in a neighbouring field.
    // null means "no currency here": a renderer then takes it from the UI component
    // (amount_input.currencySuffix). This is what lets a server switch the currency of an amount with
    // a single patch instead of reissuing the whole form schema.
    val currency: String? = null,
) : FieldValue {
    // Without the currency: a filter's query parameter is a number, not a formatted amount.
    override val plainValue: String get() = long.toString()
}

@Serializable
@SerialName("boolean_value")
public data class BooleanValue(
    val value: Boolean,
) : FieldValue {
    override val plainValue: String get() = value.toString()
}

// The result of choosing in a field with remote search: not a string but a whole entity — an id for
// the backend, a title to show, and arbitrary metadata the client can read locally and the server can
// use in a patch (see AutocompleteFieldDefinition.triggersPatch). Two metadata keys are reserved by
// the protocol, see SPEC.md §9.7.
@Serializable
@SerialName("entity_value")
public data class EntityValue(
    val id: String,
    val title: String,
    val rawMetadata: Map<String, String>? = null,
) : FieldValue {
    // The id, not the title: what leaves is the identifier the backend understands, not what the user
    // happened to see.
    override val plainValue: String get() = id
}
