package io.github.youndie.kompot.form.standard

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FormConditionsTest {
    @Test
    fun `EqualsCondition is satisfied only when the referenced field equals expectedValue`() {
        val condition = EqualsCondition("auto_numbering", BooleanValue(true))

        assertTrue(condition.evaluate { fieldId -> if (fieldId == "auto_numbering") BooleanValue(true) else null })
        assertFalse(condition.evaluate { fieldId -> if (fieldId == "auto_numbering") BooleanValue(false) else null })
    }

    @Test
    fun `EqualsCondition is not satisfied when the referenced field has no value yet`() {
        val condition = EqualsCondition("auto_numbering", BooleanValue(true))

        assertFalse(condition.evaluate { null })
    }

    @Test
    fun `NotEqualsCondition is satisfied when the referenced field has NO value yet — the documented quirk`() {
        // The source states this behaviour outright: it holds while a field is not filled in at all
        // (null != expectedValue) — the "visible until the box is ticked" case.
        val condition = NotEqualsCondition("auto_numbering", BooleanValue(true))

        assertTrue(condition.evaluate { null })
    }

    @Test
    fun `NotEqualsCondition is not satisfied once the referenced field equals expectedValue`() {
        val condition = NotEqualsCondition("auto_numbering", BooleanValue(true))

        assertFalse(condition.evaluate { fieldId -> if (fieldId == "auto_numbering") BooleanValue(true) else null })
    }

    @Test
    fun `NotEqualsCondition is satisfied when the referenced field has a different value`() {
        val condition = NotEqualsCondition("auto_numbering", BooleanValue(true))

        assertTrue(condition.evaluate { fieldId -> if (fieldId == "auto_numbering") BooleanValue(false) else null })
    }

    @Test
    fun `both conditions only look at the field they were built with — ignoring everything else`() {
        val condition = EqualsCondition("a", BooleanValue(true))

        assertFalse(condition.evaluate { fieldId -> if (fieldId == "b") BooleanValue(true) else null })
    }
}
