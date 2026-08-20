package io.github.youndie.kompot.wizard

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import io.github.youndie.kompot.form.FieldValue
import io.github.youndie.kompot.wizard.core.WizardTransition

// The body of a wizard's resume request, mirroring FormPatchRequest in :kompot-forms: the client sends
// typed FieldValue instances — what a FormController has already collected — rather than raw strings.
// Converting them into whatever flat representation a server-side engine expects is the application's
// business, and this module needs to know nothing about it.
@Serializable
data class WizardResumeRequest(
    val transition: WizardTransition,
    val values: Map<String, @Polymorphic FieldValue> = emptyMap(),
)
