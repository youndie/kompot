package io.github.youndie.kompot.experiments

// Encodes assigned experiment variants into one string for transport in an HTTP header
// ("expId1=variant1,expId2=variant2") — the same principle as the ETag in :kompot-ktor: auxiliary
// response metadata travels beside the body in a header, not inside a KompotComponent. Components
// must know nothing about experiments; this is purely a transport concern.
//
// It lives in this module rather than in the Ktor or client one so that the encoding format has ONE
// source of truth, used by the server that writes it and the client that reads it, with no risk of
// the two drifting apart.
// It does not escape "=" or "," inside ids: Experiment.id and Variant.id are always plain
// identifiers here, and full JSON for a list of string pairs would be overkill.
object ExperimentHeaderCodec {
    // The HTTP header name: one constant for both ends of the transport — the server sets it, the
    // client reads it — so a typo cannot make the two sides disagree.
    const val HEADER_NAME: String = "X-Bdui-Experiments"

    fun encode(assignments: Map<String, String>): String = assignments.entries.joinToString(",") { (id, variant) -> "$id=$variant" }

    fun decode(header: String?): Map<String, String> =
        header
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.mapNotNull { entry ->
                val parts = entry.split("=", limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }?.toMap()
            ?: emptyMap()
}
