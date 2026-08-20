package io.github.youndie.kompot.form

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private data class PayloadFakeValue(
    val value: String,
) : FieldValue

private data class PayloadFakeRequiredRule(
    override val errorMessage: String,
) : ValidationRule {
    override fun validate(
        value: FieldValue?,
        getFieldValue: (fieldId: String) -> FieldValue?,
    ): Boolean = value is PayloadFakeValue && value.value.isNotBlank()
}

private data class PayloadFakeEqualsCondition(
    val fieldId: String,
    val expectedValue: FieldValue,
) : FormCondition {
    override fun evaluate(getFieldValue: (fieldId: String) -> FieldValue?): Boolean = getFieldValue(fieldId) == expectedValue
}

private data class PayloadFakeFieldDefinition(
    override val fieldId: String,
    override val rules: List<ValidationRule> = emptyList(),
    override val visibleIf: FormCondition? = null,
    override val triggersPatch: Boolean = false,
) : FormFieldDefinition

// getPayload() and getRawValues() have DIFFERENT contracts (see FormController.kt): the payload is
// what actually goes to the server on submit — visible fields only, valid fields only, null when the
// form is invalid — while rawValues is what the patchFetcher needs, so the backend sees EVERYTHING
// the user typed, including in hidden or invalid fields. Their disagreement is the contract, not a
// bug, and these tests pin exactly that difference.
class FormControllerPayloadTest {
    private fun schema() =
        FormSchema(
            formId = "test",
            fields =
                listOf(
                    PayloadFakeFieldDefinition("name", rules = listOf(PayloadFakeRequiredRule("required"))),
                    PayloadFakeFieldDefinition("comment"),
                    PayloadFakeFieldDefinition(
                        "hidden_field",
                        visibleIf = PayloadFakeEqualsCondition("comment", PayloadFakeValue("show")),
                    ),
                ),
        )

    @Test
    fun `getPayload returns only visible fields that have a value — keyed by fieldId`() {
        val controller = FormController(schema())
        controller.onValueChanged("name", PayloadFakeValue("Ivan"))

        val payload = controller.getPayload()

        assertEquals(mapOf("name" to PayloadFakeValue("Ivan")), payload)
    }

    @Test
    fun `getPayload omits fields that were never given a value`() {
        val controller = FormController(schema())
        controller.onValueChanged("name", PayloadFakeValue("Ivan"))
        controller.onValueChanged("comment", PayloadFakeValue("show"))
        // hidden_field becomes visible but holds no value

        val payload = controller.getPayload()

        assertNotNull(payload)
        assertFalse("hidden_field" in payload)
    }

    @Test
    fun `getPayload is null the moment any VISIBLE field has an error — regardless of other fields`() {
        val controller = FormController(schema())
        controller.onValueChanged("name", PayloadFakeValue("")) // required, but empty
        controller.onFieldBlurred("name")

        assertNull(controller.getPayload())
    }

    @Test
    fun `getRawValues includes a hidden field's value — unlike getPayload`() {
        val controller = FormController(schema())
        controller.onValueChanged("name", PayloadFakeValue("Ivan"))
        controller.onValueChanged("hidden_field", PayloadFakeValue("still there"))
        // comment stays empty, so hidden_field stays hidden as far as getPayload is concerned

        val rawValues = controller.getRawValues()

        assertEquals(PayloadFakeValue("still there"), rawValues["hidden_field"])
        assertNull(controller.getPayload()?.get("hidden_field"))
    }

    @Test
    fun `getRawValues ignores validity entirely — unlike getPayload`() {
        val controller = FormController(schema())
        controller.onValueChanged("name", PayloadFakeValue("")) // required, invalid
        controller.onFieldBlurred("name")

        assertEquals(PayloadFakeValue(""), controller.getRawValues()["name"])
        assertNull(controller.getPayload())
    }

    @Test
    fun `getRawValues omits fields that were never given a value — same as getPayload`() {
        val controller = FormController(schema())
        controller.onValueChanged("name", PayloadFakeValue("Ivan"))

        assertFalse("comment" in controller.getRawValues())
    }
}
