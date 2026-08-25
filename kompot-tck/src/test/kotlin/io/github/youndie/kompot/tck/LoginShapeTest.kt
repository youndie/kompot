package io.github.youndie.kompot.tck

import io.github.youndie.kompot.spec.KompotSpecResources
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The kit assumed the way in is an ordinary kompot form and posted an envelope built from
// loginValues. The toolkit never required that — kompot-auth is a single update_session action and
// everything around it is the application's — so a server exchanging a one-time code through a plain
// DTO is conformant and could not be logged into.
//
// What it cost was not one check: without a token every secured endpoint answers 401, so the schema,
// component-id and pagination checks all report findings about a server that has none of those
// defects.
class LoginShapeTest {
    private val schemas = KompotSpecResources(root = "kompot-spec").schemas()

    private val openApi =
        Json.decodeFromString(
            JsonObject.serializer(),
            """
            {
              "paths": {
                "/auth/otp/verify": { "post": { "x-kompot-endpoint-kind": "submit", "responses": { "200": { "content": { "application/json": {} } } } } },
                "/screens/home": {
                  "get": {
                    "x-kompot-endpoint-kind": "screen",
                    "security": [ { "bearer": [] } ],
                    "responses": { "200": { "content": { "application/json": { "schema": { "${'$'}ref": "kompot.profile.schema.json#/${'$'}defs/KompotComponent" } } } } }
                  }
                }
              }
            }
            """.trimIndent(),
        )

    private val session = """{"type":"update_session","accessToken":"tok-1"}"""
    private val screen = """{"type":"text","id":"t","text":"Home"}"""

    // A server that answers the screen only when the token arrived, so a run that failed to log in
    // cannot look like a run that did.
    private class OtpServer(
        private val expectedLoginBody: String?,
    ) : TckTransport {
        val calls = mutableListOf<Triple<String, String, String?>>()
        val authorized = mutableListOf<String>()

        override suspend fun request(
            method: String,
            path: String,
            headers: Map<String, String>,
            body: String?,
        ): TckResponse {
            calls += Triple(method, path, body)
            val bearer = headers["Authorization"]
            if (bearer != null) authorized += path
            return when {
                path == "/auth/otp/verify" ->
                    if (body != null && body == expectedLoginBody) {
                        TckResponse(200, emptyMap(), """{"type":"update_session","accessToken":"tok-1"}""")
                    } else {
                        TckResponse(400, emptyMap(), """{"error":"the login body was not the plain DTO this server takes"}""")
                    }

                bearer == "Bearer tok-1" -> TckResponse(200, emptyMap(), """{"type":"text","id":"t","text":"Home"}""")
                else -> TckResponse(401, emptyMap(), "")
            }
        }
    }

    private val plainDto = """{"msisdn":"+37255500000","code":"0000"}"""

    private fun config(
        loginBody: kotlinx.serialization.json.JsonElement? = null,
        bearerToken: String? = null,
        loginPath: String? = "/auth/otp/verify",
    ) = TckConfig(
        schemas = schemas,
        openApi = openApi,
        loginPath = loginPath,
        loginValues = mapOf("msisdn" to JsonPrimitive("+37255500000"), "code" to JsonPrimitive("0000")),
        loginBody = loginBody,
        bearerToken = bearerToken,
        allowStateChangingChecks = false,
    )

    @Test
    fun `a login body of the application's own shape is posted verbatim`() {
        val server = OtpServer(expectedLoginBody = plainDto)
        val body = buildJsonObject { put("msisdn", "+37255500000"); put("code", "0000") }

        val report = runBlocking { TckRunner(server, config(loginBody = body)).run() }

        assertEquals(emptyList(), report.findings, report.toString())
        assertTrue("/screens/home" in server.authorized, "the secured screen was never walked with a token")
    }

    // The control: the same server without the field. The envelope is what the kit sent before, the
    // server refuses it, and every secured endpoint behind it reports a defect it does not have.
    @Test
    fun `without it the envelope is refused and the findings are about the wrong thing`() {
        val server = OtpServer(expectedLoginBody = plainDto)

        val report = runBlocking { TckRunner(server, config()).run() }

        assertTrue(report.findings.any { it.check == "auth" }, report.toString())
        assertTrue(report.findings.size > 1, "expected the 401s behind the failed login too: $report")
        assertTrue("/screens/home" !in server.authorized)
    }

    @Test
    fun `a token handed over is used and no login is attempted`() {
        val server = OtpServer(expectedLoginBody = null)

        val report = runBlocking { TckRunner(server, config(bearerToken = "tok-1")).run() }

        assertEquals(emptyList(), report.findings, report.toString())
        assertTrue(server.calls.none { it.second == "/auth/otp/verify" }, "the kit tried to log in anyway: ${server.calls}")
    }

    // The care the report asks for: a handed-over token must not become a header the transport always
    // adds, or the check that a secured endpoint refuses an anonymous caller goes green while proving
    // the opposite.
    //
    // What holds that today is the call site — the anonymous probe passes no headers at all rather
    // than asking authHeaders for none — so this case watches the probe rather than the helper. Told
    // apart by mutation: making authHeaders unconditional leaves it green, and handing the probe
    // authHeaders(endpoint) turns it red.
    @Test
    fun `the anonymous check still asks without the handed-over token`() {
        val server = OtpServer(expectedLoginBody = null)

        runBlocking { TckRunner(server, config(bearerToken = "tok-1")).run() }

        val anonymous = server.calls.count { it.second == "/screens/home" } - server.authorized.count { it == "/screens/home" }
        assertTrue(anonymous >= 1, "every call to the secured screen carried the token: ${server.calls}")
    }
}
