package io.github.youndie.kompot.form

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private data class CfTextValue(
    val value: String,
) : FieldValue

private data class CfBooleanValue(
    val value: Boolean,
) : FieldValue

// A local stand-in for the RequiredIf rule that :form-standard provides: form-core cannot depend on
// a plug-in that depends on it.
private data class CfRequiredIfRule(
    val targetFieldId: String,
    val expectedValue: FieldValue,
    override val errorMessage: String,
) : ValidationRule {
    override fun validate(
        value: FieldValue?,
        getFieldValue: (fieldId: String) -> FieldValue?,
    ): Boolean {
        if (getFieldValue(targetFieldId) != expectedValue) return true
        return value is CfTextValue && value.value.isNotBlank()
    }
}

private data class CfFieldDefinition(
    override val fieldId: String,
    override val rules: List<ValidationRule> = emptyList(),
) : FormFieldDefinition

private fun crossFieldSchema() =
    FormSchema(
        formId = "test",
        fields =
            listOf(
                CfFieldDefinition("has_relay_node"),
                CfFieldDefinition(
                    "relay_node",
                    rules =
                        listOf(
                            CfRequiredIfRule(
                                targetFieldId = "has_relay_node",
                                expectedValue = CfBooleanValue(true),
                                errorMessage = "Specify the relay node resource",
                            ),
                        ),
                ),
            ),
    )

class FormControllerCrossFieldValidationTest {
    @Test
    fun `field is valid empty while the target field does not match`() {
        val controller = FormController(crossFieldSchema())
        controller.onValueChanged("has_relay_node", CfBooleanValue(false))

        controller.onFieldBlurred("relay_node")

        assertNull(controller.getTypedState<CfTextValue>("relay_node").error)
    }

    @Test
    fun `field becomes required once the target field matches`() {
        val controller = FormController(crossFieldSchema())
        controller.onValueChanged("has_relay_node", CfBooleanValue(true))

        controller.onFieldBlurred("relay_node")

        assertEquals(
            "Specify the relay node resource",
            controller.getTypedState<CfTextValue>("relay_node").error,
        )
    }

    @Test
    fun `field with a value passes even when required by the target field`() {
        val controller = FormController(crossFieldSchema())
        controller.onValueChanged("has_relay_node", CfBooleanValue(true))
        controller.onValueChanged("relay_node", CfTextValue("40817..."))

        controller.onFieldBlurred("relay_node")

        assertNull(controller.getTypedState<CfTextValue>("relay_node").error)
    }

    @Test
    fun `changing the referenced field immediately refreshes an already-shown cross-field error`() {
        val controller = FormController(crossFieldSchema())
        controller.onValueChanged("has_relay_node", CfBooleanValue(true))
        controller.onFieldBlurred("relay_node")
        assertEquals(
            "Specify the relay node resource",
            controller.getTypedState<CfTextValue>("relay_node").error,
        )

        // Unticking the checkbox makes relay_node optional again. The error must disappear AT ONCE,
        // without waiting for another blur on relay_node itself.
        controller.onValueChanged("has_relay_node", CfBooleanValue(false))

        assertNull(controller.getTypedState<CfTextValue>("relay_node").error)
    }

    @Test
    fun `untouched dependent field is not eagerly validated on an unrelated change`() {
        val controller = FormController(crossFieldSchema())

        // relay_node was never touched — no blur — so ticking the checkbox must not "touch" it by
        // itself and show an error prematurely.
        controller.onValueChanged("has_relay_node", CfBooleanValue(true))

        val state = controller.getTypedState<CfTextValue>("relay_node")
        assertNull(state.error)
        assertEquals(false, state.changed)
    }

    @Test
    fun `markAllAsChanged also applies cross-field rules`() {
        val controller = FormController(crossFieldSchema())
        controller.onValueChanged("has_relay_node", CfBooleanValue(true))

        controller.markAllAsChanged()

        assertEquals(
            "Specify the relay node resource",
            controller.getTypedState<CfTextValue>("relay_node").error,
        )
        assertNull(controller.getPayload())
    }
}

class FormControllerServerErrorTest {
    @Test
    fun `setFieldError highlights a field reported invalid by the backend`() {
        val controller = FormController(FormSchema("test", listOf(CfFieldDefinition("amount"))))
        controller.onValueChanged("amount", CfTextValue("1000000"))

        // For instance a saga rejected the operation in its validation phase. The client does not
        // recompute that itself; it just highlights the field.
        controller.setFieldError("amount", "Not enough capacity on the resource")

        val state = controller.getTypedState<CfTextValue>("amount")
        assertEquals("Not enough capacity on the resource", state.error)
        assertEquals(CfTextValue("1000000"), state.value) // the value is left alone
        assertNull(controller.getPayload()) // submission stays blocked until the error clears
    }

    @Test
    fun `setFieldError with null clears a previously set error`() {
        val controller = FormController(FormSchema("test", listOf(CfFieldDefinition("amount"))))
        controller.setFieldError("amount", "Not enough capacity on the resource")

        controller.setFieldError("amount", null)

        assertNull(controller.getTypedState<CfTextValue>("amount").error)
    }
}
