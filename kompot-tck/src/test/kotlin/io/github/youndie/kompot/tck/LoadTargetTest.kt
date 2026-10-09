package io.github.youndie.kompot.tck

import io.github.youndie.kompot.spec.KompotSpecResources
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// A load names the address it reads, and SPEC.md §16.1 makes that address a GET of kind `load`: a
// screen behind it would hand the client a tree where it decodes an action, a submit an address that
// only answers POST. Both look fine on the page and fail on the first press.
class LoadTargetTest {
    private val schemas = KompotSpecResources(root = "kompot-spec").schemas()

    private val openApi =
        json.decodeFromString(
            JsonObject.serializer(),
            """
            {
              "paths": {
                "/screens/catalog":          { "get":  { "x-kompot-endpoint-kind": "screen", "responses": { "200": { "content": { "application/json": {} } } } } },
                "/ui/catalog/results":       { "get":  { "x-kompot-endpoint-kind": "load",   "responses": { "200": { "content": { "application/json": {} } } } } },
                "/screens/other":            { "get":  { "x-kompot-endpoint-kind": "screen", "responses": { "200": { "content": { "application/json": {} } } } } },
                "/submit/filters":           { "post": { "x-kompot-endpoint-kind": "submit", "responses": { "200": { "content": { "application/json": {} } } } } }
              }
            }
            """.trimIndent(),
        )

    // Every GET answers the same body: a screen with one load button, which is also what a `load`
    // endpoint answers here — not an action, so the schema check of that kind has something to say.
    private fun report(
        url: String,
        body: (String) -> String = { screen(url) },
    ): TckReport =
        runBlocking {
            TckRunner(PathBody(body), TckConfig(schemas = schemas, openApi = openApi)).run()
        }

    private fun screen(url: String) =
        """{"type":"button","id":"b","text":"Acme","action":{"type":"load","url":"$url"}}"""

    private fun loadFindings(url: String) = report(url).findings.filter { it.check == "load" }

    @Test
    fun `a load aimed at a load endpoint is fine and the query does not matter`() {
        assertEquals(emptyList(), loadFindings("/ui/catalog/results?brand=acme"))
    }

    @Test
    fun `a load aimed at nothing the description declares is reported`() {
        assertTrue(loadFindings("/ui/catalog/elsewhere").any { "does not declare" in it.message })
    }

    @Test
    fun `a load aimed at a screen or at a POST is reported`() {
        assertTrue(loadFindings("/screens/other").any { "kind \"screen\"" in it.message })
        assertTrue(loadFindings("/submit/filters").any { "does not declare" in it.message })
    }

    // A load endpoint answers an action, so one declaring no schema is still held to KompotAction: here
    // it answers a tree, and that is a finding; answering an action is not.
    @Test
    fun `a load endpoint with no declared schema is checked as an action`() {
        val wrong =
            report("/ui/catalog/results").findings.filter {
                it.check == "schema" &&
                    it.target == "/ui/catalog/results"
            }
        assertTrue(wrong.isNotEmpty(), wrong.toString())

        val right =
            report("/ui/catalog/results") { path ->
                if (path.startsWith("/ui/catalog/results")) {
                    """{"type":"update","updates":[{"componentId":"results","component":{"type":"text","id":"results","text":"acme"}}],"deeplink":"app://catalog?brand=acme"}"""
                } else {
                    screen("/ui/catalog/results")
                }
            }.findings.filter { it.check == "schema" && it.target == "/ui/catalog/results" }
        assertEquals(emptyList(), right)
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

private class PathBody(
    private val body: (String) -> String,
) : TckTransport {
    override suspend fun request(
        method: String,
        path: String,
        headers: Map<String, String>,
        body: String?,
    ): TckResponse = TckResponse(status = 200, headers = emptyMap(), body = this.body(path))
}
