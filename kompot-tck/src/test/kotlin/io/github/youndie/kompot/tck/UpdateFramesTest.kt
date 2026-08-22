package io.github.youndie.kompot.tck

import io.github.youndie.kompot.spec.KompotSpecResources
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The live channel was the one endpoint kind a walk could not reach, so a conforming server and a
// plausible-looking wrong one were indistinguishable to the kit. A recording closes that without a
// connection: the frames are what the protocol describes, the transport is not.
class UpdateFramesTest {
    private val schemas = KompotSpecResources(root = "kompot-spec").schemas()

    private val openApi =
        json.decodeFromString(
            JsonObject.serializer(),
            """
            {
              "paths": {
                "/updates": { "get": { "x-kompot-endpoint-kind": "updates_stream", "responses": { "200": { "content": { "text/event-stream": {} } } } } }
              }
            }
            """.trimIndent(),
        )

    private val frame = """{"componentId":"total","component":{"type":"text","id":"total","text":"42"}}"""

    private fun findings(recording: String): List<TckFinding> =
        runBlocking {
            TckRunner(
                EmptyTransport(),
                TckConfig(schemas = schemas, openApi = openApi, recordedUpdateStreams = mapOf("/updates" to recording)),
            ).run()
        }.findings.filter { it.check == "updates" }

    @Test
    fun `a well-formed recording passes, heartbeat and all`() {
        val clean =
            """
            event: ping

            event: update
            data: $frame

            : an ordinary comment

            data: $frame

            """.trimIndent()

        assertEquals(emptyList(), findings(clean))
    }

    // The payload is held to the same closed profile as any screen: a frame is a way of delivering a
    // component, not a way around the vocabulary.
    @Test
    fun `a frame carrying a type outside the profile is reported`() {
        val reported = findings("""data: {"componentId":"x","component":{"type":"nonesuch","id":"x"}}""")

        assertTrue(reported.isNotEmpty(), "an unknown component type slipped through the frame check")
    }

    @Test
    fun `a frame that is not an UpdateComponentMessage is reported`() {
        assertTrue(findings("""data: {"component":{"type":"text","id":"t","text":"42"}}""").isNotEmpty())
    }

    // What a first attempt at SSE gets wrong: a payload written straight into the stream, so the line
    // break inside it ends the line and the rest is no longer a data field.
    @Test
    fun `a payload broken across raw lines is reported rather than quietly repaired`() {
        val broken = "data: {\"componentId\":\"total\",\n\"component\":{\"type\":\"text\",\"id\":\"t\",\"text\":\"42\"}}"

        val reported = findings(broken)

        // Both halves of the damage are named: the orphaned line, and the field left holding half a
        // value. A lenient reader would have glued them back together and reported neither.
        assertTrue(reported.any { "belongs to no SSE field" in it.message }, reported.toString())
        assertTrue(reported.any { "not one JSON value" in it.message }, reported.toString())
    }

    @Test
    fun `a heartbeat carrying data is reported`() {
        val reported = findings("event: ping\ndata: $frame")

        assertTrue(reported.any { "heartbeat" in it.message }, reported.toString())
    }

    // Without a recording the check has no target, and the report says so through its counter rather
    // than passing silently.
    @Test
    fun `without a recording the check reports no target and the endpoint is named as skipped`() {
        val report =
            runBlocking {
                TckRunner(EmptyTransport(), TckConfig(schemas = schemas, openApi = openApi)).run()
            }

        // Zero rather than absent, and that is the useful answer: a check whose counter reads 0 says
        // out loud that it had nothing to look at, which is the whole reason the counters exist.
        assertEquals(0, report.exercised["updates"])
        assertTrue(report.skipped.single().reason.contains("recordedUpdateStreams"), report.skipped.toString())
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

// The frame check reads a recording, never the network: a transport that answers nothing is enough,
// and proves the check needs no connection.
private class EmptyTransport : TckTransport {
    override suspend fun request(
        method: String,
        path: String,
        headers: Map<String, String>,
        body: String?,
    ): TckResponse = TckResponse(status = 200, headers = emptyMap(), body = "")
}
