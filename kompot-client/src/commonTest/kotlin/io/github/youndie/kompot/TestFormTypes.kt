package io.github.youndie.kompot

import io.github.youndie.kompot.form.FieldValue
import io.github.youndie.kompot.form.FormFieldDefinition
import io.github.youndie.kompot.form.ValidationRule

    // A field, a value and a rule of the test's own instead of types from a concrete plug-in. The
    // engine carries a FormSchema and a Map<String, FieldValue> without knowing one concrete field
    // type, and a test should know no more, or the module gains a dependency its production code
    // does not have.

internal data class TestField(
    override val fieldId: String,
    override val rules: List<ValidationRule> = emptyList(),
) : FormFieldDefinition

internal data class TestValue(
    val text: String,
) : FieldValue {
    override val plainValue: String get() = text
}

    // The simplest "required": neither an empty string nor a missing value passes.
internal data class TestRequiredRule(
    override val errorMessage: String = "required",
) : ValidationRule {
    override fun validate(
        value: FieldValue?,
        getFieldValue: (fieldId: String) -> FieldValue?,
    ): Boolean = (value as? TestValue)?.text?.isNotBlank() == true
}
