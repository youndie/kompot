package io.github.youndie.kompot.tck

import io.github.youndie.kompot.spec.KompotSpecResources
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Who receives which topic — the question §16.9 named as covered by nothing. It is the one check that
// needs two subjects and a live connection at once, and the one whose silence means nothing on its
// own: a channel that delivered nothing to anybody looks exactly like a channel that kept its
// subjects apart.
class UpdateIsolationTest {
    private val schemas = KompotSpecResources(root = "kompot-spec").schemas()

    private val openApi =
        json.decodeFromString(
            JsonObject.serializer(),
            """
            {
              "paths": {
                "/login": {
                  "post": {
                    "x-kompot-endpoint-kind": "submit",
                    "security": [],
                    "responses": { "200": { "content": { "application/json": {} } } }
                  }
                },
                "/home": {
                  "get": {
                    "x-kompot-endpoint-kind": "live_screen",
                    "security": [ { "bearer": [] } ],
                    "responses": {
                      "200": {
                        "content": {
                          "application/json": {
                            "schema": { "${'$'}ref": "kompot-realtime.schema.json#/${'$'}defs/KompotScreenResponse" }
                          }
                        }
                      },
                      "401": {}
                    }
                  }
                },
                "/submit": {
                  "post": {
                    "x-kompot-endpoint-kind": "submit",
                    "security": [ { "bearer": [] } ],
                    "responses": { "200": { "content": { "application/json": {} } } }
                  }
                },
                "/updates": {
                  "get": {
                    "x-kompot-endpoint-kind": "updates_stream",
                    "security": [ { "bearer": [] } ],
                    "parameters": [ { "name": "topic", "in": "query", "required": true } ],
                    "responses": { "200": { "content": { "text/event-stream": {} } } }
                  }
                }
              }
            }
            """.trimIndent(),
        )

    private fun config(
        withSecondIdentity: Boolean = true,
        withTrigger: Boolean = true,
    ) = TckConfig(
        schemas = schemas,
        openApi = openApi,
        loginPath = "/login",
        loginValues = mapOf("user" to JsonPrimitive("alice")),
        secondIdentity =
            if (!withSecondIdentity) {
                null
            } else {
                TckIdentity(name = "bob", loginPath = "/login", loginValues = mapOf("user" to JsonPrimitive("bob")))
            },
        updateTrigger =
            if (!withTrigger) {
                null
            } else {
                TckUpdateTrigger(screenPath = "/home", path = "/submit", body = JsonObject(emptyMap()))
            },
        // Short enough that a suite does not wait, long enough that the two subscriptions are in place
        // before the frame is raised.
        updateWindowMillis = 120,
    )

    private fun run(
        server: TckTransport,
        config: TckConfig = config(),
    ): TckReport = runBlocking { TckRunner(server, config).run() }

    private fun TckReport.isolation() = findings.filter { it.check == "updates-isolation" }

    @Test
    fun `a server that keeps two subjects apart passes, and says how much it looked at`() {
        val report = run(FakeChannelServer())

        assertEquals(emptyList(), report.isolation(), report.toString())
        // The other half of the same assertion: a green verdict out of a check that never ran would
        // read identically. One topic compared plus one live probe.
        assertEquals(2, report.exercised["updates-isolation"], report.toString())
    }

    @Test
    fun `a frame of another subject's topic is reported`() {
        val reported = run(FakeChannelServer(leaks = true)).isolation()

        assertTrue(reported.any { "home:alice" in it.message }, reported.toString())
    }

    // THE CONTROL. Without it the check is worthless: a run where nothing was ever delivered sees an
    // empty stream on the other subject's side and calls it isolation.
    @Test
    fun `a trigger that raises nothing is inconclusive rather than green`() {
        val reported = run(FakeChannelServer(raisesAnything = false)).isolation()

        assertTrue(reported.any { "inconclusive" in it.message }, reported.toString())
    }

    // The half that needs no connection at all: one topic for two subjects is a shared channel, and a
    // topic naming a subject is the server's own statement that the data is personal (§10.4).
    @Test
    fun `the same personal topic handed to two subjects is reported without listening to anything`() {
        val reported = run(FakeChannelServer(sharedTopic = true), config(withTrigger = false)).isolation()

        assertTrue(reported.any { "same topic" in it.message }, reported.toString())
        assertTrue(reported.none { "inconclusive" in it.message }, "nothing was listened to, so nothing is inconclusive")
    }

    // A capture is a stream like any other and is held to the same frame rules — a server whose
    // recording was clean and whose live channel is not would otherwise pass.
    @Test
    fun `a malformed frame in the live capture is reported by the frame rules`() {
        val reported = run(FakeChannelServer(frame = """{"componentId":"x"}""")).findings.filter { it.check == "updates" }

        assertTrue(reported.any { "(live)" in it.target }, reported.toString())
    }

    @Test
    fun `without a second identity the check reports no target and the endpoint is named as skipped`() {
        val report = run(FakeChannelServer(), config(withSecondIdentity = false))

        assertEquals(0, report.exercised["updates-isolation"])
        assertTrue(
            report.skipped.any { it.path == "/updates" && "secondIdentity" in it.reason },
            report.skipped.toString(),
        )
    }

    // A transport that cannot listen is not a server defect. The offline half still runs; the live
    // half claims nothing and the report says the endpoint was not walked.
    @Test
    fun `a transport that cannot listen claims nothing about isolation`() {
        val report = run(DeafServer())

        assertEquals(emptyList(), report.isolation(), report.toString())
        assertTrue(report.skipped.any { it.path == "/updates" }, report.skipped.toString())
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        const val FRAME = """{"componentId":"t","component":{"type":"text","id":"t","text":"43"}}"""
    }
}

// A server with two subjects and one update channel. Everything it does that matters is in stream():
// who is allowed onto a topic, and who is handed a frame once one is raised.
private open class FakeChannelServer(
    // Delivers to whoever is listening, topic or no topic — the defect the check exists for.
    private val leaks: Boolean = false,
    // Whether the trigger produces a frame at all. False is not a leak and not isolation: it is a run
    // that proves nothing, and must not look like a pass.
    private val raisesAnything: Boolean = true,
    // Both subjects are given one personal topic — a leak visible without listening.
    private val sharedTopic: Boolean = false,
    private val frame: String = """{"componentId":"t","component":{"type":"text","id":"t","text":"43"}}""",
) : TckTransport {
    private var raised: String? = null

    override suspend fun request(
        method: String,
        path: String,
        headers: Map<String, String>,
        body: String?,
    ): TckResponse {
        if (path == "/login") {
            val subject =
                Json
                    .parseToJsonElement(body.orEmpty())
                    .jsonObject
                    .getValue("values")
                    .jsonObject
                    .getValue("user")
                    .jsonPrimitive
                    .content
            return TckResponse(200, emptyMap(), """{"type":"update_session","accessToken":"token-$subject"}""")
        }

        val subject = subjectOf(headers) ?: return TckResponse(401, emptyMap(), "")
        return when {
            path.startsWith("/home") ->
                TckResponse(200, emptyMap(), """{"screen":{"type":"text","id":"t","text":"42"},"realtimeTopic":"${topicOf(subject)}"}""")

            path.startsWith("/submit") -> {
                if (raisesAnything) raised = topicOf(subject)
                TckResponse(200, emptyMap(), """{"type":"update_session","accessToken":"token-$subject"}""")
            }

            else -> TckResponse(404, emptyMap(), "")
        }
    }

    override suspend fun stream(
        path: String,
        headers: Map<String, String>,
        windowMillis: Long,
    ): TckStreamCapture? {
        val subject = subjectOf(headers) ?: return TckStreamCapture(401, "")
        val topic = path.substringAfter("topic=")

        // What a server that keeps its subjects apart does: a topic naming somebody else is refused at
        // the door rather than subscribed and then filtered.
        if (!leaks && topic.substringAfter(':') != subject) return TckStreamCapture(403, "")

        delay(windowMillis)
        val delivered = if (leaks) raised != null else raised == topic
        return TckStreamCapture(200, if (delivered) "data: $frame\n\n" else "")
    }

    private fun topicOf(subject: String) = if (sharedTopic) "home:alice" else "home:$subject"

    private fun subjectOf(headers: Map<String, String>) =
        headers["Authorization"]?.removePrefix("Bearer ")?.removePrefix("token-")?.takeIf { it.isNotEmpty() }
}

// The same server through a transport that only knows request/response: stream() keeps its default.
private class DeafServer : FakeChannelServer() {
    override suspend fun stream(
        path: String,
        headers: Map<String, String>,
        windowMillis: Long,
    ): TckStreamCapture? = null
}
