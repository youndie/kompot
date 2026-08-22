package io.github.youndie.kompot.tck

import io.github.youndie.kompot.spec.KompotSpecResources
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// An endpoint addressed by naming a thing was unreachable for a walk: the kit cannot invent an
// identifier that exists, and skipped it. In a tracker that is the screen of one task — the largest
// tree the server emits, and the only one no run had ever seen.
class ParameterisedEndpointsTest {
    private val schemas = KompotSpecResources(root = "kompot-spec").schemas()

    private val openApi =
        json.decodeFromString(
            JsonObject.serializer(),
            """
            {
              "paths": {
                "/forms/task/{task}": { "get": { "x-kompot-endpoint-kind": "screen", "responses": { "200": { "content": { "application/json": {} } } } } },
                "/screens/board":     { "get": { "x-kompot-endpoint-kind": "screen", "responses": { "200": { "content": { "application/json": {} } } } } }
              }
            }
            """.trimIndent(),
        )

    private fun runWith(config: TckConfig): Pair<TckReport, List<String>> {
        val asked = mutableListOf<String>()
        val report =
            runBlocking {
                TckRunner(RecordingTransport(asked, """{"type":"text","id":"t","text":"Task"}"""), config).run()
            }
        return report to asked
    }

    private fun config(pathParameters: Map<String, Map<String, String>> = emptyMap()) =
        TckConfig(schemas = schemas, openApi = openApi, pathParameters = pathParameters)

    @Test
    fun `without a value the templated endpoint is skipped and says which config would fill it`() {
        val (report, asked) = runWith(config())

        assertTrue(asked.none { it.startsWith("/forms/task") }, asked.toString())
        assertTrue(report.skipped.single().reason.contains("pathParameters"), report.skipped.toString())
    }

    // The placeholder is substituted, not merely accepted: what the transport is asked for is the
    // concrete address. Checking the report alone would pass even if the kit called "/forms/task/{task}"
    // literally and got a 404 shaped like a screen.
    @Test
    fun `with a value the endpoint is walked at its concrete address`() {
        val (report, asked) = runWith(config(mapOf("/forms/task/{task}" to mapOf("task" to "TAC-1"))))

        assertTrue("/forms/task/TAC-1" in asked, asked.toString())
        assertTrue(asked.none { "{" in it }, asked.toString())
        assertEquals(emptyList(), report.skipped)
    }

    // Every check counts it, not just the one that happens to run first — the point of the fix is that
    // the endpoint stops being invisible to the walk as a whole.
    @Test
    fun `a resolved endpoint becomes a target of the ordinary checks`() {
        val (before, _) = runWith(config())
        val (after, _) = runWith(config(mapOf("/forms/task/{task}" to mapOf("task" to "TAC-1"))))

        assertEquals(1, before.exercised.getValue("schema"))
        assertEquals(2, after.exercised.getValue("schema"))
        assertEquals(2, after.exercised.getValue("component-id"))
    }

    @Test
    fun `a query is appended to the address the walk calls`() {
        val (_, asked) =
            runWith(
                TckConfig(
                    schemas = schemas,
                    openApi = openApi,
                    queryParameters = mapOf("/screens/board" to mapOf("view" to "compact")),
                ),
            )

        assertTrue(asked.any { it == "/screens/board?view=compact" }, asked.toString())
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

// Records what was asked for, which is the only way to tell substitution from acceptance.
private class RecordingTransport(
    private val asked: MutableList<String>,
    private val body: String,
) : TckTransport {
    override suspend fun request(
        method: String,
        path: String,
        headers: Map<String, String>,
        body: String?,
    ): TckResponse {
        asked += path
        return TckResponse(status = 200, headers = emptyMap(), body = this.body)
    }
}
