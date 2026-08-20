package io.github.youndie.kompot.form

// The open contract for a field's visibility condition (visibleIf). Not sealed: concrete conditions
// live in plug-in modules, on the same scheme as ValidationRule and FormFieldDefinition — the core
// does not know which conditions exist.
interface FormCondition {
    fun evaluate(getFieldValue: (fieldId: String) -> FieldValue?): Boolean
}
