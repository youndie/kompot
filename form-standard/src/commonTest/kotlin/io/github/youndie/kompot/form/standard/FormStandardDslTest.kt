package io.github.youndie.kompot.form.standard

import io.github.youndie.kompot.form.formSchema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FormStandardDslTest {
    @Test
    fun `textField builds a TextFieldDefinition carrying mask — keyboardType and rules`() {
        val schema =
            formSchema("form") {
                textField("phone", keyboardType = KeyboardType.PHONE, mask = "+998 (##) ###-##-##") {
                    required("Required")
                }
            }

        val field = assertIs<TextFieldDefinition>(schema.fields.single())
        assertEquals("phone", field.fieldId)
        assertEquals(KeyboardType.PHONE, field.keyboardType)
        assertEquals("+998 (##) ###-##-##", field.mask)
        assertEquals(1, field.rules.size)
        assertIs<RequiredRule>(field.rules.single())
    }

    @Test
    fun `checkboxField — selectionField and autocompleteField each build their matching definition type`() {
        val schema =
            formSchema("form") {
                checkboxField("auto")
                selectionField("status")
                autocompleteField("beneficiary", dataSourceId = "beneficiaries_search")
            }

        assertIs<CheckboxFieldDefinition>(schema.fields[0])
        assertIs<SelectionFieldDefinition>(schema.fields[1])
        val autocomplete = assertIs<AutocompleteFieldDefinition>(schema.fields[2])
        assertEquals("beneficiaries_search", autocomplete.dataSourceId)
    }

    @Test
    fun `every field definition defaults rules to empty and visibleIf-triggersPatch to their neutral values`() {
        val schema = formSchema("form") { textField("optional") }

        val field = schema.fields.single() as TextFieldDefinition
        assertTrue(field.rules.isEmpty())
        assertNull(field.visibleIf)
        assertFalse(field.triggersPatch)
    }

    @Test
    fun `visibleIf and triggersPatch are threaded through for every field builder — not just textField`() {
        val condition = equals("is_gift", BooleanValue(true))
        val schema =
            formSchema("form") {
                amountField("amount", visibleIf = condition, triggersPatch = true)
            }

        val field = schema.fields.single() as AmountFieldDefinition
        assertEquals(condition, field.visibleIf)
        assertTrue(field.triggersPatch)
    }

    @Test
    fun `equals and notEquals build the matching condition types`() {
        assertIs<EqualsCondition>(equals("a", BooleanValue(true)))
        assertIs<NotEqualsCondition>(notEquals("a", BooleanValue(true)))
    }

    @Test
    fun `required — regex — requiredIf and maxAmountFromField each append exactly one rule of the right type`() {
        val schema =
            formSchema("form") {
                textField("code") {
                    required("Required")
                    regex("^[A-Z0-9]{8}$", "Wrong format")
                    requiredIf("is_gift", BooleanValue(true), "Conditionally required")
                }
                amountField("amount") {
                    maxAmountFromField("source", errorMessage = "Not enough left")
                }
            }

        val codeRules = (schema.fields[0] as TextFieldDefinition).rules
        assertEquals(3, codeRules.size)
        assertIs<RequiredRule>(codeRules[0])
        assertIs<RegexRule>(codeRules[1])
        assertIs<RequiredIfRule>(codeRules[2])

        val amountRules = (schema.fields[1] as AmountFieldDefinition).rules
        assertIs<MaxAmountRule>(amountRules.single())
    }
}
