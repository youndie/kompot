package io.github.youndie.kompot.form.standard

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EntityValueValidationTest {
    @Test
    fun `RequiredRule rejects an entity with a blank id — cleared autocomplete`() {
        val rule = RequiredRule("required")

        assertFalse(rule.validate(EntityValue(id = "", title = "Ivan"), getFieldValue = { null }))
    }

    @Test
    fun `RequiredRule accepts an entity with a non-blank id`() {
        val rule = RequiredRule("required")

        assertTrue(rule.validate(EntityValue(id = "42", title = "Ivan"), getFieldValue = { null }))
    }

    @Test
    fun `RequiredIfRule rejects a blank entity when the target condition matches`() {
        val rule = RequiredIfRule("is_gift", BooleanValue(true), "required")

        val result =
            rule.validate(
                EntityValue(id = "", title = ""),
                getFieldValue = { fieldId -> if (fieldId == "is_gift") BooleanValue(true) else null },
            )

        assertFalse(result)
    }

    @Test
    fun `MaxAmountRule rejects an amount above the remaining amount in the referenced entity's metadata`() {
        val rule = MaxAmountRule(balanceFieldId = "source", errorMessage = "not enough left")
        val source = EntityValue(id = "src1", title = "Source", rawMetadata = mapOf("balance" to "1000"))
        val getFieldValue = { fieldId: String -> if (fieldId == "source") source else null }

        assertFalse(rule.validate(AmountValue(1500L), getFieldValue = getFieldValue))
        assertTrue(rule.validate(AmountValue(1000L), getFieldValue = getFieldValue))
        assertTrue(rule.validate(AmountValue(500L), getFieldValue = getFieldValue))
    }

    @Test
    fun `MaxAmountRule does not block when nothing is selected yet`() {
        val rule = MaxAmountRule(balanceFieldId = "source", errorMessage = "not enough left")

        assertTrue(rule.validate(AmountValue(999_999L), getFieldValue = { null }))
    }

    @Test
    fun `MaxAmountRule ignores values of a different type`() {
        val rule = MaxAmountRule(balanceFieldId = "source", errorMessage = "not enough left")

        assertTrue(rule.validate(TextValue("not an amount"), getFieldValue = { null }))
    }
}
