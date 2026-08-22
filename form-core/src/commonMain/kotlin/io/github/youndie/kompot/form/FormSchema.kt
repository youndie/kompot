package io.github.youndie.kompot.form

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable

// The form schema, the only concrete (@Serializable) type in form-core. The field and rule lists
// hold open interfaces, so the set of field kinds is not fixed when the core module compiles and is
// plugged in from outside.
@Serializable
data class FormSchema(
    val formId: String,
    val fields: List<@Polymorphic FormFieldDefinition>,
)

// The open contract for a form field. Implementations — a text field, an amount, a checkbox, a
// selector and so on — live in plug-in feature modules.
interface FormFieldDefinition {
    val fieldId: String
    val rules: List<@Polymorphic ValidationRule>

    // The visibility condition, evaluated locally with no server round trip (see FormController). A
    // field whose condition does not hold is neither rendered nor included in the payload, even if a
    // value was once typed into it.
    val visibleIf: @Polymorphic FormCondition? get() = null

    // Changing this field's value requires the backend to recompute the form — picking a template
    // that should auto-fill other fields, say. See FormController.requestPatchIfNeeded and FormPatch.
    val triggersPatch: Boolean get() = false

    // What the field holds before anybody types. Nothing carried one, which costs most inside a
    // multi-step scenario: `back` with no pre-fill is a form somebody fills in twice.
    //
    // A defaulted member here is not enough on its own — kotlinx.serialization writes the properties a
    // CONCRETE class declares, so a field type that does not override this one simply never puts it on
    // the wire. Every field in :form-standard does; a plug-in type of your own has to as well, or it
    // will accept an initial value in Kotlin and drop it in transit.
    val initialValue: @Polymorphic FieldValue? get() = null
}

// The open contract for a validation rule. Concrete rules (required, regex, requiredIf and so on)
// are defined alongside the fields that use them.
interface ValidationRule {
    val errorMessage: String

    // getFieldValue gives a rule access to neighbouring fields, which cross-field validation needs —
    // "required if the checkbox on another field is ticked", for instance. Business validation does
    // not belong here: that is the backend's job, and the client only highlights the fieldId its
    // answer names.
    fun validate(
        value: FieldValue?,
        getFieldValue: (fieldId: String) -> FieldValue?,
    ): Boolean
}
