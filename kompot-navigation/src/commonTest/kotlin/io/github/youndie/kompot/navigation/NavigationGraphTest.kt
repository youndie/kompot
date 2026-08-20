package io.github.youndie.kompot.navigation

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

    @Test
    fun `routeFor returns null for a deeplink not in the graph`() {
        val graph = NavigationGraph(routes = listOf(ScreenRoute(deeplink = "app://promo", endpoint = "/api/v1/promo")))

        assertNull(graph.routeFor("app://home"))
    }
}
