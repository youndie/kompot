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
    // Which run of the scenario this is. It arrives from the start endpoint in a response HEADER — the
    // body there is a KompotFormResponse, shared with plain forms, and has no slot for it — and it had
    // no described way back in at all: this type carried no field for it and §16.7 names response
    // headers only. Two implementations reading the specification would each invent a request header
    // and neither could talk to the other's client.
    //
    // A field rather than a header of our own, because this type is ours: the schema describes it and
    // the conformance kit can check it, neither of which is true of a header.
    val wizardId: String? = null,
)
