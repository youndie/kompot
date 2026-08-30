package io.github.youndie.kompot.client.tck

import kotlinx.serialization.json.JsonObject

// Everything the corpus needs to know about a client, and deliberately no more — the same posture
// TckTransport takes towards a server. A client on another language satisfies this over whatever
// boundary it likes; the operations are the contract, the language is not.
//
// Values cross as JSON rather than as Kotlin types on purpose. An adapter decodes them the way its own
// client does, which is part of what is under test: a corpus that handed over ready-made objects would
// check the toolkit's decoder instead of the implementer's.
public interface KompotFormClient {
    // A FormSchema — the `schema` half of a KompotFormResponse (§9.1).
    public fun load(form: JsonObject)

    public fun set(
        fieldId: String,
        value: JsonObject,
    )

    // Validation runs on blur rather than on every keystroke (§9.5), so the corpus has to be able to
    // say when focus left a field.
    public fun blur(fieldId: String)

    public fun applyPatch(patch: JsonObject)

    // What a submit does before it sends: force validation of every field, including the untouched
    // ones (§9.5).
    public fun submit()

    public fun visibleFields(): List<String>

    public fun errors(): Map<String, String>

    // The map a submit would carry, or null when validation blocks it (§9.4, §9.5).
    public fun payload(): JsonObject?

    // What the client SENT, in order, as JSON objects: today the patch requests a field with
    // triggersPatch causes (§9.6). Everything else the corpus looks at is state the client holds, and
    // a patch is the one rule that is only observable as an outgoing call — a client that never sends
    // one, or sends two, or sends the wrong snapshot, has state that looks perfect throughout.
    //
    // null, the default, means this adapter does not record them. A case that expects requests is
    // then reported UNCHECKED rather than passed or failed: a client that cannot answer the question
    // has not answered it, and an empty list would accuse it of sending nothing.
    //
    // Defaulted so that an adapter written before this operation still compiles — the same
    // compatibility rule the wire keeps for a new field.
    public fun requests(): List<JsonObject>? = null
}
