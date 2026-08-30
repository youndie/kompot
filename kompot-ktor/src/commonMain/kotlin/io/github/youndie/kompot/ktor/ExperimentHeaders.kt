package io.github.youndie.kompot.ktor

import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.github.youndie.kompot.experiments.ExperimentHeaderCodec

// Puts the experiment assignments the application has already made (experimentId -> variantId, see
// ExperimentAssigner in :experiments-core) into a response header — the same trick as the ETag in
// ETagResponses.kt: auxiliary metadata travels beside the body in a header rather than inside the
// component tree itself.
//
// Call it BEFORE responding: Ktor does not allow adding headers once the body has started going out.
// This is not an ETag equivalent. Exposure must be tracked by the client on every real render, so
// the header belongs only on non-cacheable responses; otherwise a repeat 304 would "lose" the
// assignment for a client that never re-requests it.
public fun ApplicationCall.setExperimentHeader(assignments: Map<String, String>) {
    if (assignments.isEmpty()) return
    response.header(ExperimentHeaderCodec.HEADER_NAME, ExperimentHeaderCodec.encode(assignments))
}
