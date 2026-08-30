package io.github.youndie.kompot.form.standard

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import io.github.youndie.kompot.form.FieldValue
import io.github.youndie.kompot.form.ValidationRule

@Serializable
@SerialName("required")
public data class RequiredRule(
    override val errorMessage: String,
) : ValidationRule {
    override fun validate(
        value: FieldValue?,
        getFieldValue: (fieldId: String) -> FieldValue?,
    ): Boolean {
        // No value at all means the field is certainly not filled in.
        if (value == null) return false

        // Beyond that it depends on what kind of value it is.
        return when (value) {
            is TextValue -> value.text.isNotBlank()

            is AmountValue -> true

            is BooleanValue -> true

            // The user cleared an autocomplete query without choosing anything: the id is empty.
            is EntityValue -> value.id.isNotBlank()

            // A fallback for value types from other plug-ins: a value that exists counts as filled in.
            else -> true
        }
    }
}

@Serializable
@SerialName("regex")
public data class RegexRule(
    val pattern: String,
    override val errorMessage: String,
) : ValidationRule {
    override fun validate(
        value: FieldValue?,
        getFieldValue: (fieldId: String) -> FieldValue?,
    ): Boolean {
        // A regex deliberately lets null through. A mandatory field is caught by RequiredRule, which is
        // why that one belongs first in the list; for an optional field, empty is a valid state.
        if (value == null) return true

        // The typed value has to become a string before a pattern can be applied to it.
        val stringToCheck =
            when (value) {
                is TextValue -> value.text

                is AmountValue -> value.long.toString()

                // A pattern makes no sense for a boolean, or for a value type from another plug-in.
                else -> return true
            }

        // An empty string is not matched against the pattern either, for the same reason.
        if (stringToCheck.isBlank()) return true

        // matches(), not containsMatchIn(): the pattern has to describe the whole value.
        return Regex(pattern).matches(stringToCheck)
    }
}

// Cross-field validation: a field is mandatory only while another field equals expectedValue — a gift
// message required only when "this is a gift" is ticked, for instance.
//
// Business validation does not belong here: that is the server's job, and the client only highlights
// the fieldId its answer names (see FormController.setFieldError).
@Serializable
@SerialName("required_if")
public data class RequiredIfRule(
    val targetFieldId: String,
    val expectedValue: @Polymorphic FieldValue,
    override val errorMessage: String,
) : ValidationRule {
    override fun validate(
        value: FieldValue?,
        getFieldValue: (fieldId: String) -> FieldValue?,
    ): Boolean {
        // The target field does not hold the expected value, so this one is optional and valid even
        // when empty.
        if (getFieldValue(targetFieldId) != expectedValue) return true

        if (value == null) return false
        return when (value) {
            is TextValue -> value.text.isNotBlank()
            is EntityValue -> value.id.isNotBlank()
            else -> true
        }
    }
}

// Cross-field check: the amount must not exceed what is left on the selected entity. A quick UX
// pre-check, NOT a replacement for the server's: it only works while the remaining amount is already
// known locally, in the rawMetadata of the chosen EntityValue.
//
// balanceFieldId, balanceMetadataKey and the "balance" key are not banking vocabulary but a key the
// protocol reserves (SPEC.md §9.7, KompotProtocol.METADATA_KEY_BALANCE): a remaining amount belongs
// to a gift card, a quota or a bundle of minutes just as well.
//
// The final decision stays with the server, which may refuse the operation even after this rule
// passed — what is left could have changed between rendering the form and submitting it.
@Serializable
@SerialName("max_amount_from_field")
public data class MaxAmountRule(
    val balanceFieldId: String,
    val balanceMetadataKey: String = "balance",
    override val errorMessage: String,
) : ValidationRule {
    override fun validate(
        value: FieldValue?,
        getFieldValue: (fieldId: String) -> FieldValue?,
    ): Boolean {
        if (value !is AmountValue) return true
        val balance =
            (getFieldValue(balanceFieldId) as? EntityValue)
                ?.rawMetadata
                ?.get(balanceMetadataKey)
                ?.toLongOrNull()
                ?: return true // Nothing is selected yet, so the rule stays out of the way
        return value.long <= balance
    }
}
