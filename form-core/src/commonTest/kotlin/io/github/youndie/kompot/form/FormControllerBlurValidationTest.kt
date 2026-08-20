package io.github.youndie.kompot.form

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private data class TestValue(
    val value: String,
) : FieldValue

private data class TestRequiredRule(
    override val errorMessage: String,
) : ValidationRule {
    override fun validate(value: FieldValue?, getFieldValue: (fieldId: String) -> FieldValue?): Boolean = value is TestValue && value.value.isNotBlank()
}

private data class TestFieldDefinition(
    override val fieldId: String,
    override val rules: List<ValidationRule>,
) : FormFieldDefinition

private fun testSchema() =
    FormSchema(
        formId = "test",
        fields = listOf(TestFieldDefinition("name", listOf(TestRequiredRule("required")))),
    )

class FormControllerBlurValidationTest {
    @Test
    fun `typing does not trigger validation`() {
        val controller = FormController(testSchema())

        controller.onValueChanged("name", TestValue(""))

        assertNull(controller.getTypedState<TestValue>("name").error)
    }

    @Test
    fun `blur triggers validation for that field`() {
        val controller = FormController(testSchema())

        controller.onValueChanged("name", TestValue(""))
        controller.onFieldBlurred("name")

        assertEquals("required", controller.getTypedState<TestValue>("name").error)
    }

    @Test
    fun `editing after blur hides the old error until the next blur`() {
        val controller = FormController(testSchema())

        controller.onValueChanged("name", TestValue(""))
        controller.onFieldBlurred("name")
        assertEquals("required", controller.getTypedState<TestValue>("name").error)

        controller.onValueChanged("name", TestValue("Alice"))
        assertNull(controller.getTypedState<TestValue>("name").error)

        controller.onFieldBlurred("name")
        assertNull(controller.getTypedState<TestValue>("name").error)
    }

    @Test
    fun `blurring an unknown field id is a no-op`() {
        val controller = FormController(testSchema())

        controller.onFieldBlurred("does-not-exist")

        assertNull(controller.getTypedState<TestValue>("name").error)
    }
}
