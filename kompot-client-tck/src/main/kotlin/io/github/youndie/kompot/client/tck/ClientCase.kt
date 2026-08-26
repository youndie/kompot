package io.github.youndie.kompot.client.tck

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// One case: a form, a sequence of things a person does to it, and what must be true afterwards.
//
// Not "a body and a schema" — that is the server corpus (§17), which shows what a response looks like.
// This one shows what a client must DECIDE, which is the half nothing checked.
@Serializable
data class ClientCase(
    val id: String,
    // The clause it holds a client to. A case without one is a case nobody can argue with.
    val clause: String,
    val title: String,
    // What goes wrong when a client gets this wrong — in a sentence, for whoever reads the failure
    // rather than the case.
    val why: String,
    val form: JsonObject,
    val steps: List<ClientStep> = emptyList(),
    val expect: ClientExpectation,
)

@Serializable
sealed interface ClientStep {
    @Serializable
    @SerialName("set")
    data class Set(
        val fieldId: String,
        val value: JsonObject,
    ) : ClientStep

    @Serializable
    @SerialName("blur")
    data class Blur(
        val fieldId: String,
    ) : ClientStep

    @Serializable
    @SerialName("patch")
    data class Patch(
        val patch: JsonObject,
    ) : ClientStep

    @Serializable
    @SerialName("submit")
    data object Submit : ClientStep
}

// Every expectation is optional: a case asserts what it is about and stays silent about the rest, so
// that a failure names one rule rather than a screenful of unrelated state.
@Serializable
data class ClientExpectation(
    val visibleFields: List<String>? = null,
    val payload: JsonObject? = null,
    // true when validation must stop the submit — payload is then null rather than empty, and the
    // difference matters: an empty map is a form with nothing in it, null is a form that refused.
    val payloadBlocked: Boolean? = null,
    val errors: Map<String, String>? = null,
    val noErrors: List<String>? = null,
    // The calls the client made, in order — see KompotFormClient.requests. The whole list: "one patch
    // and no more" is most of what §9.6 says, and a check that only looked for the presence of one
    // would pass a client that sends a patch per keystroke.
    val requests: List<JsonObject>? = null,
)

@Serializable
data class ClientCorpusIndex(
    val cases: List<String>,
    // The case format, described beside the cases. A runner in another language validates what it
    // parsed against this instead of inferring the vocabulary of `expect` from whichever cases happen
    // to exist — the inference that turned a list of field ids into a flag, and a case into one that
    // asserts nothing while reporting green.
    val schema: String = "client-corpus.schema.json",
)
