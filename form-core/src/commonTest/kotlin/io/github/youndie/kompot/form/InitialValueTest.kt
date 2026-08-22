package io.github.youndie.kompot.form

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private data class TestText(val text: String) : FieldValue {
    override val plainValue: String get() = text
}

private data class SeededField(
    override val fieldId: String,
    override val rules: List<ValidationRule> = emptyList(),
    override val initialValue: FieldValue? = null,
) : FormFieldDefinition

// A field could carry no starting value, which costs most inside a multi-step scenario: going back to
// a step meant typing it again. The value only means anything if the controller reads it — a schema
// property nobody consults is the shape of defect this toolkit keeps finding.
class InitialValueTest {
    @Test
    fun `a field starts on the value its definition carries`() {
        val controller = FormController(FormSchema("f", listOf(SeededField("title", initialValue = TestText("Draft")))))

        assertEquals(TestText("Draft"), controller.fieldsState.value.getValue("title").value)
    }

    // What the caller passes is a draft being resumed; what the schema carries is a suggestion for an
    // empty form. The draft has to win, or reopening a half-filled step overwrites what was typed.
    @Test
    fun `a value handed to the controller wins over the schema's`() {
        val controller =
            FormController(
                FormSchema("f", listOf(SeededField("title", initialValue = TestText("Draft")))),
                initialValues = mapOf("title" to TestText("What I typed")),
            )

        assertEquals(TestText("What I typed"), controller.fieldsState.value.getValue("title").value)
    }

    @Test
    fun `a field without one still starts empty`() {
        val controller = FormController(FormSchema("f", listOf(SeededField("title"))))

        assertNull(controller.fieldsState.value.getValue("title").value)
    }
}
