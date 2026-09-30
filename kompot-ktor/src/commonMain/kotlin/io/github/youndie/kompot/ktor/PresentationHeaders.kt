package io.github.youndie.kompot.ktor

import io.github.youndie.kompot.navigation.PresentationHeader
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header

// Asks the client to show THIS response as a layer over the screen it was opened from — a sheet or a
// dialog (SPEC.md §12.1, §16.7) — decided per answer, where the route's `presentation` decides per
// address. The same address can answer a state that is a sheet and one that is not.
//
// Call it BEFORE responding, as setExperimentHeader: Ktor does not allow adding headers once the body has
// started going out. Called before respondKompotComponentCached, the header rides on the 304 as well,
// which is what a client reusing its cached body needs to show it the same way.
public fun ApplicationCall.setPresentationHeader(presentation: String) {
    response.header(PresentationHeader.HEADER_NAME, presentation)
}
