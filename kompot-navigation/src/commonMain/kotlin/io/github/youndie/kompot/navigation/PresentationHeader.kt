package io.github.youndie.kompot.navigation

// How ONE response asks to be shown, beside its body (SPEC.md §12.1, §16.7). The route's `presentation`
// says it for an address; this says it for an answer, because the same address can answer a state that
// belongs in a sheet (an order awaiting confirmation) and one that does not (the same order completed) —
// and the server knows which only once it has built the answer.
//
// A header rather than a field in the body for the reason the experiment assignments are one
// (ExperimentHeaderCodec): a plain screen answers a bare component tree, which has nowhere to carry
// metadata without changing the base of the whole hierarchy. And a header degrades for free: a client
// that predates it never reads it and shows the screen as it always did.
public object PresentationHeader {
    // One constant for both ends: the server sets it, the client reads it.
    public const val HEADER_NAME: String = "X-Kompot-Presentation"

    /**
     * How a client that can draw [supported] shows a response that carried [header] and was reached
     * through [route] (null when it was not reached through the graph).
     *
     * The response wins over the route when it names a presentation the client can draw — it knows the
     * state, the route only the address — `screen` included, which takes a route's sheet back to a
     * screen for this one answer. A header the client cannot draw, or none, leaves the decision to the
     * route, and without a route the answer is a screen.
     */
    public fun presentedAs(
        header: String?,
        route: ScreenRoute? = null,
        supported: Set<String> = ScreenRoutePresentation.known,
    ): String =
        header?.trim()?.lowercase()?.takeIf { it in supported }
            ?: route?.presentedAs(supported)
            ?: ScreenRoutePresentation.SCREEN
}
