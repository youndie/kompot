package io.github.youndie.kompot.form.standard

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import io.github.youndie.kompot.form.FieldValue
import io.github.youndie.kompot.form.FormCondition

@Serializable
@SerialName("equals")
public data class EqualsCondition(
    val fieldId: String,
    val expectedValue: @Polymorphic FieldValue,
) : FormCondition {
    override fun evaluate(getFieldValue: (fieldId: String) -> FieldValue?): Boolean = getFieldValue(fieldId) == expectedValue
}

// Unlike equals, this holds while a field is not filled in at all (null != expectedValue), which is
// what "visible until the box is ticked" needs — a document number that only matters while automatic
// numbering is off, for instance.
@Serializable
@SerialName("not_equals")
public data class NotEqualsCondition(
    val fieldId: String,
    val expectedValue: @Polymorphic FieldValue,
) : FormCondition {
    override fun evaluate(getFieldValue: (fieldId: String) -> FieldValue?): Boolean = getFieldValue(fieldId) != expectedValue
}
