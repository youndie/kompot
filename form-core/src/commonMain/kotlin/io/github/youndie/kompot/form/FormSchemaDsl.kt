package io.github.youndie.kompot.form

@DslMarker
annotation class FormDsl

// A minimal rule builder that knows nothing about concrete rules (required, regex and the like).
// Feature modules add their own convenience functions as extensions over rule(...), for example:
//   fun ValidationRulesBuilder.required(errorMessage: String) = rule(RequiredRule(errorMessage))
@FormDsl
class ValidationRulesBuilder {
    private val rules = mutableListOf<ValidationRule>()

    fun rule(rule: ValidationRule) {
        rules += rule
    }

    fun build(): List<ValidationRule> = rules.toList()
}

// A minimal schema builder whose single operation is adding a ready-made field. Feature modules add
// their own convenience functions as extensions over field(...), for example:
//   fun FormSchemaBuilder.textField(fieldId: String, ...) = field(TextFieldDefinition(...))
@FormDsl
class FormSchemaBuilder(
    private val formId: String,
) {
    private val fields = mutableListOf<FormFieldDefinition>()

    fun field(definition: FormFieldDefinition) {
        fields += definition
    }

    fun build(): FormSchema = FormSchema(formId = formId, fields = fields.toList())
}

fun formSchema(
    formId: String,
    block: FormSchemaBuilder.() -> Unit,
): FormSchema = FormSchemaBuilder(formId).apply(block).build()
