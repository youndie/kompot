package io.github.youndie.kompot.tck

import io.github.youndie.kompot.spec.KompotSpecResources
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// `text` is the whole string and the spans are its runs, so the two must agree. Kept in two places,
// one string drifts — and here it drifts invisibly: a client reading the spans shows one sentence, a
// client reading the flat form shows another, and neither can tell.
class TextSpansTest {
    private val schemas = KompotSpecResources(root = "kompot-spec").schemas()

    private val openApi =
        json.decodeFromString(
            JsonObject.serializer(),
            """
            {
              "paths": {
                "/screens/prose": { "get": { "x-kompot-endpoint-kind": "screen", "responses": { "200": { "content": { "application/json": {} } } } } }
              }
            }
            """.trimIndent(),
        )

    private fun findings(body: String): List<TckFinding> =
        runBlocking {
            TckRunner(FixedScreen(body), TckConfig(schemas = schemas, openApi = openApi)).run()
        }.findings.filter { it.check == "text-spans" }

    @Test
    fun `a flat string matching its spans passes`() {
        val body =
            """{"type":"text","id":"t","text":"see the item","spans":[{"text":"see "},{"text":"the item"}]}"""

        assertEquals(emptyList(), findings(body))
    }

    @Test
    fun `a flat string that disagrees with its spans is reported`() {
        val body =
            """{"type":"text","id":"t","text":"see the item","spans":[{"text":"see "},{"text":"something else"}]}"""

        val reported = findings(body)

        assertTrue(reported.any { "see the item" in it.message && "see something else" in it.message }, reported.toString())
    }

    // A node without spans is the ordinary case and must not be dragged into this at all.
    @Test
    fun `a node with no spans is left alone`() {
        assertEquals(emptyList(), findings("""{"type":"text","id":"t","text":"plain"}"""))
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

private class FixedScreen(
    private val body: String,
) : TckTransport {
    override suspend fun request(
        method: String,
        path: String,
        headers: Map<String, String>,
        body: String?,
    ): TckResponse = TckResponse(status = 200, headers = emptyMap(), body = this.body)
}
