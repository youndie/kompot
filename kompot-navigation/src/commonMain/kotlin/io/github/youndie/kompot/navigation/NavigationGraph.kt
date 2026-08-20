package io.github.youndie.kompot.navigation

import kotlinx.serialization.Serializable

// One plain screen of the graph: the deeplink the client opens it by — the same deeplink that a
// NavigateAction elsewhere already carries — and the endpoint to fetch the KompotComponent tree from,
// a bare GET with no form or schema behind it. `title` is the copy for the screen's app bar, decided
// by the server rather than the client, like the rest of this toolkit's content.
//
// The graph deliberately does not cover screens that need client code of their own — an idempotency
// key, a live-update subscription, a multi-step flow — none of which fits in a single endpoint. It is
// for plain "fetch a tree and show it" screens; the point is that exactly this class of screen can
// then be added on the backend without releasing a client, because one generic renderer draws any
// route in the graph without knowing in advance what stands behind it.
@Serializable
data class ScreenRoute(
    val deeplink: String,
    val endpoint: String,
    val title: String? = null,
)

@Serializable
data class NavigationGraph(
    val routes: List<ScreenRoute>,
) {
    fun routeFor(deeplink: String): ScreenRoute? = routes.find { it.deeplink == deeplink }
}
