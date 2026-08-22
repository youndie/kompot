package io.github.youndie.kompot.tck

import io.github.youndie.kompot.spec.KompotSpecResources
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// A green run that skipped an endpoint reads exactly like a green run that covered it. The per-check
// counters cannot tell them apart — the other endpoints keep every check busy — so the report has to
// name what it never looked at.
class SkippedEndpointsTest {
    private val schemas = KompotSpecResources(root = "kompot-spec").schemas()

    private val openApi =
        json.decodeFromString(
            JsonObject.serializer(),
            """
            {
              "paths": {
                "/screens/home":     { "get": { "x-kompot-endpoint-kind": "screen", "responses": { "200": { "content": { "application/json": {} } } } } },
                "/forms/task/{task}":{ "get": { "x-kompot-endpoint-kind": "form", "responses": { "200": { "content": { "application/json": {} } } } } },
                "/screens/legacy":   { "get": { "x-kompot-endpoint-kind": "screen", "deprecated": "true", "responses": { "200": { "content": { "application/json": {} } } } } },
                "/updates":          { "get": { "x-kompot-endpoint-kind": "updates_stream", "responses": { "200": { "content": { "text/event-stream": {} } } } } },
                "/submit/move":      { "post": { "x-kompot-endpoint-kind": "submit", "responses": { "200": { "content": { "application/json": {} } }, "400": {}, "409": {} } } }
              }
            }
            """.trimIndent(),
        )

    private val report =
        runBlocking {
            TckRunner(
                FixedBodyTransport("""{"type":"text","id":"t","text":"Home"}"""),
                TckConfig(schemas = schemas, openApi = openApi),
            ).run()
        }

    private fun reasonFor(path: String): String? = report.skipped.firstOrNull { it.path == path }?.reason

    // The reported case: the most complicated screen in a product sat behind a path parameter and the
    // run was green without ever fetching it.
    @Test
    fun `an endpoint with a path parameter is named, with the reason`() {
        val reason = reasonFor("/forms/task/{task}")

        assertTrue(reason != null && "path parameter" in reason, report.skipped.toString())
    }

    @Test
    fun `each kind of exclusion gets its own reason rather than one blanket line`() {
        assertTrue(reasonFor("/screens/legacy")!!.contains("deprecated"), report.skipped.toString())
        assertTrue(reasonFor("/updates")!!.contains("event-stream"), report.skipped.toString())
        assertTrue(reasonFor("/submit/move")!!.contains("submitPayloads"), report.skipped.toString())
    }

    @Test
    fun `an endpoint the walk did reach is not listed as skipped`() {
        assertEquals(null, reasonFor("/screens/home"))
    }

    // The finding is only useful if a reader meets it, and a clean run is exactly when nobody goes
    // looking.
    @Test
    fun `the skipped list is printed on a clean run too`() {
        assertTrue(report.isClean, report.findings.toString())
        assertTrue("Not walked" in report.toString(), report.toString())
        assertTrue("/forms/task/{task}" in report.toString(), report.toString())
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

// A transport of its own rather than the private one next door: the file-private stub belongs to the
// test that owns it.
private class FixedBodyTransport(
    private val body: String,
) : TckTransport {
    override suspend fun request(
        method: String,
        path: String,
        headers: Map<String, String>,
        body: String?,
    ): TckResponse = TckResponse(status = 200, headers = emptyMap(), body = this.body)
}
