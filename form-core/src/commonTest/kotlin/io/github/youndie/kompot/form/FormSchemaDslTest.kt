package io.github.youndie.kompot.form

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private data class FakeRule(
    override val errorMessage: String,
) : ValidationRule {
    override fun validate(
        value: FieldValue?,
        getFieldValue: (fieldId: String) -> FieldValue?,
    ) = true
}

private data class FakeField(
    override val fieldId: String,
    override val rules: List<ValidationRule> = emptyList(),
) : FormFieldDefinition

class FormSchemaDslTest {
    @Test
    fun `formSchema carries the formId through and collects fields in declaration order`() {
        val schema =
            formSchema("request_template") {
                field(FakeField("a"))
                field(FakeField("b"))
            }

        assertEquals("request_template", schema.formId)
        assertEquals(listOf("a", "b"), schema.fields.map { it.fieldId })
    }

    @Test
    fun `an empty block still produces a valid schema with no fields`() {
        val schema = formSchema("empty") {}

        assertEquals("empty", schema.formId)
        assertTrue(schema.fields.isEmpty())
    }

    @Test
    fun `ValidationRulesBuilder collects rules in declaration order and build is a snapshot`() {
        val builder = ValidationRulesBuilder()
        builder.rule(FakeRule("first"))
        builder.rule(FakeRule("second"))

        val firstBuild = builder.build()
        builder.rule(FakeRule("third"))
        val secondBuild = builder.build()

        assertEquals(listOf("first", "second"), firstBuild.map { it.errorMessage })
        assertEquals(listOf("first", "second", "third"), secondBuild.map { it.errorMessage })
    }

    @Test
    fun `FormSchemaBuilder build is a snapshot too — later field calls do not retroactively affect it`() {
        val builder = FormSchemaBuilder("form")
        builder.field(FakeField("a"))
        val firstBuild = builder.build()
        builder.field(FakeField("b"))

        assertEquals(listOf("a"), firstBuild.fields.map { it.fieldId })
    }
}
