package io.github.youndie.kompot.tck

import io.github.youndie.kompot.spec.KompotSpecResources
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// A graph route says what a client will decode the body as. Until `kind` existed the kit assumed
// every route yielded a component tree, so a form route — which answers a KompotFormResponse envelope
// with no discriminator of its own — was reported as "no discriminator property type", a finding no
// server could act on.
class GraphRouteKindTest {
    private val schemas = KompotSpecResources(root = "kompot-spec").schemas()

    private val openApi =
        json.decodeFromString(
            JsonObject.serializer(),
            """
            {
              "paths": {
                "/graph":         { "get": { "x-kompot-endpoint-kind": "graph",  "responses": { "200": { "content": { "application/json": {} } } } } },
                "/screens/home":  { "get": { "x-kompot-endpoint-kind": "screen", "responses": { "200": { "content": { "application/json": { "schema": { "${'$'}ref": "kompot.profile.schema.json#/${'$'}defs/KompotComponent" } } } } } } },
                "/forms/new-task":{ "get": { "x-kompot-endpoint-kind": "form",   "responses": { "200": { "content": { "application/json": { "schema": { "${'$'}ref": "kompot-forms.schema.json#/${'$'}defs/KompotFormResponse" } } } } } } },
                "/screens/sweep": { "get": { "x-kompot-endpoint-kind": "live_screen", "responses": { "200": { "content": { "application/json": {} } } } } }
              }
            }
            """.trimIndent(),
        )

    private val screen = """{"type":"text","id":"title","text":"Home"}"""
    private val form = """{"schema":{"formId":"new-task","fields":[]},"screen":$screen}"""
    private val live = """{"screen":$screen,"realtimeTopic":"sweep:ktor"}"""

    private fun graph(
        routeKindOfForm: String = "form",
        routeKindOfSweep: String = "live_screen",
    ) = """
        {
          "routes": [
            { "deeplink": "app://home", "endpoint": "/screens/home" },
            { "deeplink": "app://new-task", "endpoint": "/forms/new-task", "kind": "$routeKindOfForm" },
            { "deeplink": "app://sweep", "endpoint": "/screens/sweep", "kind": "$routeKindOfSweep" }
          ]
        }
        """.trimIndent()

    // The report's case, and the whole point: a form screen reachable through the graph.
    //
    // Scoped to its own route rather than asserting the whole run is quiet: the graph carries three
    // routes, and a test that owns all of them fails for a neighbour's reasons and reads as if this
    // one broke.
    @Test
    fun `a form route is accepted and validated as a form envelope`() {
        assertEquals(emptyList(), findings(graph()).filter { it.target == "/forms/new-task" })
    }

    // The mistake the kit could not name before: the route promises a component tree, the description
    // says the endpoint answers a form. A client following it decodes the wrong envelope and shows
    // nothing, with no error anywhere.
    @Test
    fun `a route whose kind contradicts the endpoint is reported`() {
        val reported = findings(graph(routeKindOfForm = "screen"))

        assertTrue(reported.any { "kind" in it.message }, reported.toString())
        assertTrue(reported.any { "/forms/new-task" == it.target }, reported.toString())
    }

    // The third envelope, and the one the walk had no fallback for: a screen that is not a form and
    // names the channel its updates arrive on (§10.4). Its endpoint declares no response schema on
    // purpose — that is the path where the kit picks a schema from the kind alone, and picking the
    // screen one would report a conformant server for answering an envelope where a node was
    // expected. Exactly the finding a form route used to get.
    @Test
    fun `a live_screen route is validated as a screen envelope`() {
        assertEquals(emptyList(), findings(graph()).filter { it.target == "/screens/sweep" })
    }

    @Test
    fun `a live_screen route answering a bare component tree is reported`() {
        val reported = findings(graph(), sweepBody = screen)

        assertTrue(reported.any { it.target == "/screens/sweep" }, reported.toString())
    }

    private fun findings(
        graph: String,
        sweepBody: String = live,
    ): List<TckFinding> =
        runBlocking {
            TckRunner(
                RoutingTransport(
                    mapOf(
                        "/graph" to graph,
                        "/screens/home" to screen,
                        "/forms/new-task" to form,
                        "/screens/sweep" to sweepBody,
                    ),
                ),
                TckConfig(schemas = schemas, openApi = openApi),
            ).run()
        }.findings.filter { it.check == "navigation" }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

// Unlike the stub next door this one answers per path: the check under test walks from one body to
// another, so answering everything alike would prove nothing.
private class RoutingTransport(
    private val bodies: Map<String, String>,
) : TckTransport {
    override suspend fun request(
        method: String,
        path: String,
        headers: Map<String, String>,
        body: String?,
    ): TckResponse = TckResponse(status = 200, headers = emptyMap(), body = bodies[path] ?: "{}")
}
