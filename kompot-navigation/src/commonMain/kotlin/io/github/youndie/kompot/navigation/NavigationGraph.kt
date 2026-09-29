package io.github.youndie.kompot.navigation

import kotlinx.serialization.Serializable

// What stands behind a route's endpoint, in the same vocabulary as x-kompot-endpoint-kind (SPEC.md
// §16.1). It is what tells a client which serialiser to use, and without it the graph could carry only
// component trees: a form endpoint answers a KompotFormResponse envelope, which has no discriminator
// of its own, so a client following a route had nothing to decide by.
public object ScreenRouteKind {
    public const val SCREEN: String = "screen"
    public const val FORM: String = "form"

    // A screen whose response is a KompotScreenResponse rather than a bare component tree: the same
    // tree, plus the channel its updates arrive on (§10.4).
    //
    // Its own kind rather than a second shape allowed at `screen`, for the reason `form` has one: a
    // client picks its deserialiser by kind, and a client released before this existed would meet an
    // envelope where it expects a node. §12.1 already says such a client skips the route — it loses a
    // screen it could not have subscribed to anyway, instead of failing to parse one.
    public const val LIVE_SCREEN: String = "live_screen"

    // Every kind this build can draw. A client passes what IT supports, which is not necessarily this.
    public val known: Set<String> = setOf(SCREEN, FORM, LIVE_SCREEN)
}

// How a route's screen is shown (SPEC.md §12.1): pushed as a screen of its own, or laid over the screen
// it was opened from. A hint, not a kind: the endpoint and what it answers are the same either way, and a
// client that does not know the hint opens a screen, as it did before the hint existed — which is the
// whole reason the hint lives on the route and not in a new action (B-68). `present` stays for a layer
// with no address.
public object ScreenRoutePresentation {
    public const val SCREEN: String = "screen"
    public const val SHEET: String = "sheet"
    public const val DIALOG: String = "dialog"

    // Every presentation this build knows. A client passes what IT can draw, which is not necessarily this.
    public val known: Set<String> = setOf(SCREEN, SHEET, DIALOG)
}

// One plain screen of the graph: the deeplink the client opens it by — the same deeplink that a
// NavigateAction elsewhere already carries — and the endpoint to fetch the screen from. `title` is the
// copy for the screen's app bar, decided by the server rather than the client, like the rest of this
// toolkit's content.
//
// The graph deliberately does not cover screens that need client code of their own — an idempotency
// key, a live-update subscription, a multi-step flow — none of which fits in a single endpoint. A form
// is NOT such a screen: nothing about rendering one is specific to a particular form, which is why it
// belongs here and why excluding it cost the graph most of its point.
@Serializable
public data class ScreenRoute(
    val deeplink: String,
    val endpoint: String,
    val title: String? = null,
    // A plain String and not an enum, deliberately. The rule is that a client ignores a route whose
    // kind it does not recognise (SPEC.md §12.1) — and an enum cannot obey it: an unknown constant
    // fails deserialisation of the WHOLE graph before any code gets to skip one route. The same reason
    // ColorToken is an open key.
    val kind: String = ScreenRouteKind.SCREEN,
    // An open String for the same reason as `kind`, with the opposite rule for an unknown value: an
    // unknown kind hides the route, an unknown presentation shows it as a screen. Hiding would turn a
    // sheet into a dead button on the very client the hint exists to spare.
    val presentation: String = ScreenRoutePresentation.SCREEN,
) {
    /**
     * How a client that can draw [supported] shows this route: [presentation] when it is one of them,
     * [ScreenRoutePresentation.SCREEN] otherwise — for a value from a newer server, and for a sheet on a
     * client that has no sheet.
     */
    public fun presentedAs(supported: Set<String> = ScreenRoutePresentation.known): String =
        presentation.takeIf { it in supported } ?: ScreenRoutePresentation.SCREEN
}

@Serializable
public data class NavigationGraph(
    val routes: List<ScreenRoute>,
) {
    // Defaulting to screens only is not caution for its own sake: it is exactly what a caller written
    // before `kind` existed did, so the default preserves that behaviour instead of silently handing it
    // a route it cannot draw. A client that renders forms passes ScreenRouteKind.known.
    public fun routeFor(
        deeplink: String,
        supportedKinds: Set<String> = setOf(ScreenRouteKind.SCREEN),
    ): ScreenRoute? = routes.find { it.deeplink == deeplink && it.kind in supportedKinds }
}
