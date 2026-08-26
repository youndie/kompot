package io.github.youndie.kompot.tck

import io.github.youndie.kompot.spec.KompotSpecResources
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// A patch names fields, and nothing fails when they are not fields. FormController keys by string,
// so a patch naming "balanse" applies cleanly, the value lands on a field nobody renders, and the
// screen stops updating — no exception, no log, and on screen it looks exactly like a server that has
// not answered yet.
//
// The walk could not reach it: a patch is a POST and the blind walk is GET. Declaring the endpoint
// `form` would have been worse than declaring nothing — it would enter form-fields, find no schema in
// the body, and report nothing at all, which reads as a pass.
class PatchFieldsTest {
    private val schemas = KompotSpecResources(root = "kompot-spec").schemas()

    private val openApi =
        Json.decodeFromString(
            JsonObject.serializer(),
            """
            {
              "paths": {
                "/forms/package": { "get": { "x-kompot-endpoint-kind": "form", "responses": { "200": { "content": { "application/json": {} } } } } },
                "/forms/package/patch": { "post": { "x-kompot-endpoint-kind": "patch", "responses": { "200": { "content": { "application/json": {} } } } } }
              }
            }
            """.trimIndent(),
        )

    private val form =
        """
        {"schema":{"formId":"package","fields":[{"type":"text_field","fieldId":"gigabytes","rules":[]},{"type":"text_field","fieldId":"price","rules":[]}]},
         "screen":{"type":"column","id":"c","children":[
           {"type":"text_input","id":"g","fieldId":"gigabytes","label":"GB"},
           {"type":"read_only_field","id":"p","label":"Price","value":"0","fieldId":"price"}]}}
        """.trimIndent()

    private class Server(private val patch: String, private val form: String) : TckTransport {
        override suspend fun request(
            method: String,
            path: String,
            headers: Map<String, String>,
            body: String?,
        ): TckResponse =
            when (path) {
                "/forms/package" -> TckResponse(200, emptyMap(), form)
                "/forms/package/patch" -> TckResponse(200, emptyMap(), patch)
                else -> TckResponse(404, emptyMap(), "")
            }
    }

    private fun findings(
        patch: String,
        paired: Boolean = true,
        body: Boolean = true,
    ): List<TckFinding> =
        runBlocking {
            TckRunner(
                Server(patch, form),
                TckConfig(
                    schemas = schemas,
                    openApi = openApi,
                    patchEndpoints = if (paired) mapOf("/forms/package/patch" to "/forms/package") else emptyMap(),
                    submitPayloads =
                        if (body) mapOf("/forms/package/patch" to buildJsonObject { put("formId", "package") }) else emptyMap(),
                ),
            ).run()
        }.findings.filter { it.check == "patch" }

    @Test
    fun `a patch naming only declared fields is clean`() {
        assertEquals(emptyList(), findings("""{"updates":{"price":{"type":"text_value","text":"12"}}}"""))
    }

    @Test
    fun `a patch updating a field the form does not declare is reported`() {
        val reported = findings("""{"updates":{"balanse":{"type":"text_value","text":"12"}}}""")

        assertEquals(1, reported.size, reported.toString())
        assertTrue("balanse" in reported.single().message, reported.single().message)
    }

    @Test
    fun `clearFields and focusOn are held to the same rule`() {
        val reported = findings("""{"clearFields":["gigabytse"],"focusOn":"pric"}""")

        assertEquals(2, reported.size, reported.toString())
        assertTrue(reported.any { "clears" in it.message }, reported.toString())
        assertTrue(reported.any { "focuses" in it.message }, reported.toString())
    }

    // Silence has to be told from a pass: a patch endpoint nobody paired is reported as unwalked with
    // the reason, not counted as checked.
    @Test
    fun `an unpaired patch endpoint is reported as not walked`() {
        val report =
            runBlocking {
                TckRunner(
                    Server("""{"updates":{}}""", form),
                    TckConfig(schemas = schemas, openApi = openApi),
                ).run()
            }

        assertTrue(
            report.skipped.any { it.path == "/forms/package/patch" && "patchEndpoints" in it.reason },
            report.skipped.toString(),
        )
    }

    @Test
    fun `a pairing with no body says so rather than passing`() {
        val reported = findings("""{"updates":{}}""", body = false)

        assertEquals(1, reported.size, reported.toString())
        assertTrue("submitPayloads" in reported.single().message, reported.single().message)
    }
}
