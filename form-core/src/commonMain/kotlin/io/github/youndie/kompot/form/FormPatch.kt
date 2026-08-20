package io.github.youndie.kompot.form

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable

// The backend's answer to a form "patch" (see FormController.requestPatchIfNeeded): a targeted
// update of an already-rendered schema, without fetching a new FormSchema and component tree. When a
// choice must change the SET of fields rather than only their values, the server sends a fresh
// FormSchema and component tree instead of a patch, and the screen is redrawn.
@Serializable
data class FormPatch(
    val updates: Map<String, @Polymorphic FieldValue> = emptyMap(),
    val clearFields: List<String> = emptyList(),
    val focusOn: String? = null,
)
