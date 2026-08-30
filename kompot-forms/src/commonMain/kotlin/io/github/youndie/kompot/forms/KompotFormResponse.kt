package io.github.youndie.kompot.forms

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.form.FieldValue
import io.github.youndie.kompot.form.FormSchema

// The BFF response envelope: the form schema (validation and masks, form-core) plus the render tree
// (kompot-core). :kompot-forms is the natural home for this DTO, since it already describes "a form
// as Kompot components".
@Serializable
public data class KompotFormResponse(
    val schema: FormSchema,
    val screen: KompotComponent,
    // The live-update channel topic for THIS particular response — not a global constant, but a
    // value the server decides per request (for instance "items:$userId"), so a screen can receive
    // personalised updates without leaking between users.
    // null means the screen does not support live updates and behaves as before.
    val realtimeTopic: String? = null,
)

// A form patch request (see FormController.requestPatchIfNeeded and FormPatch in form-core): the
// client sends the form's current raw state along with the fieldId whose change triggered the
// recalculation, and the server returns a FormPatch.
@Serializable
public data class FormPatchRequest(
    val formId: String,
    val fieldId: String,
    val values: Map<String, @Polymorphic FieldValue>,
)
