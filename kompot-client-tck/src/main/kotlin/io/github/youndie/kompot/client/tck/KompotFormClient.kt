package io.github.youndie.kompot.client.tck

import kotlinx.serialization.json.JsonObject

// Everything the corpus needs to know about a client, and deliberately no more — the same posture
// TckTransport takes towards a server. A client on another language satisfies this over whatever
// boundary it likes; the operations are the contract, the language is not.
//
// Values cross as JSON rather than as Kotlin types on purpose. An adapter decodes them the way its own
// client does, which is part of what is under test: a corpus that handed over ready-made objects would
// check the toolkit's decoder instead of the implementer's.
interface KompotFormClient {
    // A FormSchema — the `schema` half of a KompotFormResponse (§9.1).
    fun load(form: JsonObject)

    fun set(
        fieldId: String,
        value: JsonObject,
    )

    // Validation runs on blur rather than on every keystroke (§9.5), so the corpus has to be able to
    // say when focus left a field.
    fun blur(fieldId: String)

    fun applyPatch(patch: JsonObject)

    // What a submit does before it sends: force validation of every field, including the untouched
    // ones (§9.5).
    fun submit()

    fun visibleFields(): List<String>

    fun errors(): Map<String, String>

    // The map a submit would carry, or null when validation blocks it (§9.4, §9.5).
    fun payload(): JsonObject?
}
