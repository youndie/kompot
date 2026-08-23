package io.github.youndie.kompot.tck

import io.github.youndie.kompot.spec.KompotSpecResources
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// A form whose subject is in its identifier submits to a templated address, and the description
// declares the template. Comparing the action's resolved target with it literally reported a declared
// endpoint as undeclared — and only here: everywhere the walk FETCHES a templated address it resolves
// it correctly, which is what made the finding read like a defect of the server.
class PerformTemplatedTargetTest {
    private val schemas = KompotSpecResources(root = "kompot-spec").schemas()

    private val openApi =
        json.decodeFromString(
            JsonObject.serializer(),
            """
            {
              "paths": {
                "/screens/task":            { "get":  { "x-kompot-endpoint-kind": "screen", "responses": { "200": { "content": { "application/json": {} } } } } },
                "/submit/task-view/{task}": { "post": { "x-kompot-endpoint-kind": "submit", "responses": { "200": { "content": { "application/json": {} } } } } },
                "/submit/plain":            { "post": { "x-kompot-endpoint-kind": "screen", "responses": { "200": { "content": { "application/json": {} } } } } }
              }
            }
            """.trimIndent(),
        )

    private fun findings(url: String): List<TckFinding> {
        val screen =
            """{"type":"button","id":"b","text":"Open","action":{"type":"perform","url":"$url"}}"""
        return runBlocking {
            TckRunner(FixedBody(screen), TckConfig(schemas = schemas, openApi = openApi)).run()
        }.findings.filter { it.check == "perform" }
    }

    @Test
    fun `a resolved target matches the template the description declares`() {
        assertEquals(emptyList(), findings("/submit/task-view/TAC-1"))
    }

    // The template must not swallow everything: a different shape is still undeclared, or the check
    // would stop meaning anything the moment one templated path existed.
    @Test
    fun `a target of another shape is still reported`() {
        assertTrue(findings("/submit/task-view/TAC-1/extra").isNotEmpty())
        assertTrue(findings("/submit/elsewhere/TAC-1").isNotEmpty())
    }

    // And the kind is still checked through the template, not only the existence of a path.
    @Test
    fun `a template declared as the wrong kind is reported`() {
        assertTrue(findings("/submit/plain").any { "kind" in it.message })
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

private class FixedBody(
    private val body: String,
) : TckTransport {
    override suspend fun request(
        method: String,
        path: String,
        headers: Map<String, String>,
        body: String?,
    ): TckResponse = TckResponse(status = 200, headers = emptyMap(), body = this.body)
}
