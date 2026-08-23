package io.github.youndie.kompot.navigation

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val json = Json { classDiscriminator = "type" }

class NavigationGraphTest {
    @Test
    fun `NavigationGraph round-trips its routes including optional title`() {
        val graph =
            NavigationGraph(
                routes =
                    listOf(
                        ScreenRoute(deeplink = "app://promo", endpoint = "/api/v1/promo"),
                        ScreenRoute(deeplink = "app://catalogue/item", endpoint = "/api/v1/catalogue/item", title = "Catalogue"),
                    ),
            )

        val decoded = json.decodeFromString<NavigationGraph>(json.encodeToString(graph))

        assertEquals(graph, decoded)
    }

    @Test
    fun `routeFor finds the matching route by deeplink`() {
        val offer = ScreenRoute(deeplink = "app://catalogue/item", endpoint = "/api/v1/catalogue/item", title = "Catalogue")
        val graph = NavigationGraph(routes = listOf(ScreenRoute(deeplink = "app://promo", endpoint = "/api/v1/promo"), offer))

        assertEquals(offer, graph.routeFor("app://catalogue/item"))
    }

    // A route the client cannot draw must be invisible, not a crash: a caller that never heard of
    // `kind` behaves exactly as before, and one that renders forms opts in by naming the kinds.
    @Test
    fun `a route is only found when its kind is one the caller supports`() {
        val form = ScreenRoute(deeplink = "app://new-task", endpoint = "/forms/new-task", kind = ScreenRouteKind.FORM)
        val graph = NavigationGraph(routes = listOf(form))

        assertNull(graph.routeFor("app://new-task"))
        assertEquals(form, graph.routeFor("app://new-task", ScreenRouteKind.known))
    }

    // The reason kind is a String. An enum would fail here — not by returning null for one route, but
    // by taking the whole graph down before any route could be skipped.
    @Test
    fun `a kind from a newer server does not break the graph — it just hides that route`() {
        val decoded =
            json.decodeFromString<NavigationGraph>(
                """{"routes":[{"deeplink":"app://home","endpoint":"/screens/home"},""" +
                    """{"deeplink":"app://flow","endpoint":"/wizard/start","kind":"wizard_start"}]}""",
            )

        assertEquals(2, decoded.routes.size)
        assertNull(decoded.routeFor("app://flow", ScreenRouteKind.known))
        assertTrue(decoded.routeFor("app://home") != null)
    }

    @Test
    fun `a route without kind is a screen — so graphs written before the field keep working`() {
        val decoded = json.decodeFromString<NavigationGraph>("""{"routes":[{"deeplink":"app://home","endpoint":"/screens/home"}]}""")

        assertEquals(ScreenRouteKind.SCREEN, decoded.routes.single().kind)
    }

    @Test
    fun `routeFor returns null for a deeplink not in the graph`() {
        val graph = NavigationGraph(routes = listOf(ScreenRoute(deeplink = "app://promo", endpoint = "/api/v1/promo")))

        assertNull(graph.routeFor("app://home"))
    }
}
