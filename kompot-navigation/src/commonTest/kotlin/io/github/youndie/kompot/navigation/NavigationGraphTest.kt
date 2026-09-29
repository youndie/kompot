package io.github.youndie.kompot.navigation

import kotlinx.serialization.Serializable
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

    @Test
    fun `a route says how it is shown, and a client shows what it can draw`() {
        val confirm = ScreenRoute(deeplink = "app://confirm", endpoint = "/screens/confirm", presentation = ScreenRoutePresentation.SHEET)

        assertEquals(ScreenRoutePresentation.SHEET, confirm.presentedAs())
        assertEquals(ScreenRoutePresentation.SCREEN, confirm.presentedAs(setOf(ScreenRoutePresentation.SCREEN)))
        assertEquals(ScreenRoutePresentation.SCREEN, ScreenRoute(deeplink = "app://home", endpoint = "/screens/home").presentedAs())
    }

    // The opposite of an unknown kind: the route stays, as a screen. Hiding it would leave the button that
    // opens it dead on exactly the client the hint is meant to spare.
    @Test
    fun `a presentation from a newer server shows the route as a screen`() {
        val decoded =
            wire.decodeFromString<NavigationGraph>(
                """{"routes":[{"deeplink":"app://confirm","endpoint":"/screens/confirm","presentation":"popover"}]}""",
            )

        val route = decoded.routeFor("app://confirm")!!
        assertEquals(ScreenRoutePresentation.SCREEN, route.presentedAs())
    }

    // A client released before the field reads the same graph through its own ScreenRoute, which has no
    // `presentation`: on the wire's terms (unknown keys ignored, SPEC.md §3) it finds the route and opens
    // it the only way it knows — as a screen.
    @Test
    fun `a graph with a presentation is read by a client that predates the field`() {
        val body = wire.encodeToString(NavigationGraph(listOf(ScreenRoute("app://confirm", "/screens/confirm", presentation = "sheet"))))

        val older = wire.decodeFromString<GraphBeforePresentation>(body)

        assertEquals(listOf(RouteBeforePresentation("app://confirm", "/screens/confirm")), older.routes)
    }
}

// The shape of the graph in 0.38, as a client of that version decodes it.
@Serializable
private data class GraphBeforePresentation(
    val routes: List<RouteBeforePresentation>,
)

@Serializable
private data class RouteBeforePresentation(
    val deeplink: String,
    val endpoint: String,
    val title: String? = null,
    val kind: String = ScreenRouteKind.SCREEN,
)

// What a client reads with: unknown keys ignored, as SPEC.md §3 requires of every client.
private val wire = Json { ignoreUnknownKeys = true }
