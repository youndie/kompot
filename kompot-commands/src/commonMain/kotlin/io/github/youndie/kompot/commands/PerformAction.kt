package io.github.youndie.kompot.commands

import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.form.FieldValue
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The action that changes domain state from a plain screen. Before it existed, every mutation in the
// protocol went through submit_form, and a form is ONE identity with ONE set of values — so a list of
// actionable items had no way to say "do this to item N". A board of task cards, each with a "Move to
// In review" button, was inexpressible without a screen per card.
//
// It answers the request half only. The response half already existed: the endpoint is of kind
// `submit` (SPEC.md §16.1), so it replies with a KompotAction that the client feeds back into the
// same handler chain — the same way a form submit hands over to `navigate` or `update_session`.
// Nothing new travels back, and no new endpoint kind appears.
//
// The payload carries FieldValue rather than free-form JSON so that it stays describable by the
// schema and checkable by the conformance kit — the same vocabulary a form submit already sends in
// FormPatchRequest.values. The cost is stated in SPEC.md §2.2: the form hierarchies do not degrade, so
// an unknown value type here fails the parse of the whole screen, not just this button — the value
// types a server puts in a payload must belong to the profile the client declares.
@Serializable
@SerialName("perform")
data class PerformAction(
    val url: String,
    val payload: Map<String, @Polymorphic FieldValue> = emptyMap(),
) : KompotAction
