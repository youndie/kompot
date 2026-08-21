package io.github.youndie.kompot.form.standard

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegexRuleTest {
    private val bicRule = RegexRule(pattern = "^[A-Z0-9]{8}$|^[A-Z0-9]{11}$", errorMessage = "invalid BIC")

    @Test
    fun `matches a value that fully satisfies the pattern`() {
        assertTrue(bicRule.validate(TextValue("INIPUZ21SHB"), getFieldValue = { null }))
        assertTrue(bicRule.validate(TextValue("CHASUS33"), getFieldValue = { null }))
    }

    @Test
    fun `rejects a value that only partially matches — matches requires the whole string`() {
        assertFalse(bicRule.validate(TextValue("INIPUZ21SHBX"), getFieldValue = { null }))
        assertFalse(bicRule.validate(TextValue("inipuz21shb"), getFieldValue = { null }))
    }

    @Test
    fun `null value passes — RegexRule is not a required check — RequiredRule owns that`() {
        assertTrue(bicRule.validate(null, getFieldValue = { null }))
    }

    @Test
    fun `blank text passes even if it would not match the pattern`() {
        assertTrue(bicRule.validate(TextValue(""), getFieldValue = { null }))
        assertTrue(bicRule.validate(TextValue("   "), getFieldValue = { null }))
    }

    @Test
    fun `AmountValue is checked against its string representation`() {
        val digitsOnly = RegexRule(pattern = "^[0-9]+$", errorMessage = "digits only")

        assertTrue(digitsOnly.validate(AmountValue(12345L), getFieldValue = { null }))
    }

    @Test
    fun `a value type the rule cannot stringify passes through unchecked`() {
        assertTrue(bicRule.validate(BooleanValue(true), getFieldValue = { null }))
    }
}
