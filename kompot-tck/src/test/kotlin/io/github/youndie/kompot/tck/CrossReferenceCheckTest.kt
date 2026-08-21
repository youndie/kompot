package io.github.youndie.kompot.tck

import io.github.youndie.kompot.spec.KompotSpecResources
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The one check whose subject belongs to the application rather than to the protocol: which property
// of a rule holds a reference to a neighbouring field is decided by the field plug-in, so the kit
// takes the mapping from its config. These two runs are the difference between "configured" and
// "hardcoded" — the same response is a violation with the mapping and invisible without it.
class CrossReferenceCheckTest {
    private val schemas = KompotSpecResources(root = "kompot-spec").schemas()

    private val openApi =
        json.decodeFromString(
            JsonObject.serializer(),
            """
            {
              "paths": {
                "/form": {
                  "get": {
                    "x-kompot-endpoint-kind": "form",
                    "responses": { "200": { "content": { "application/json": {} } } }
                  }
                }
              }
            }
            """.trimIndent(),
        )

    // A form whose rule points at a field that was never declared. The screen and the schema agree on
    // "amount", so the only thing wrong here is the cross-reference.
    private val formResponse =
        """
        {
          "schema": {
            "formId": "f",
            "fields": [
              {
                "type": "text_field",
                "fieldId": "amount",
                "rules": [{ "type": "required_if", "targetFieldId": "ghost", "errorMessage": "required" }]
              }
            ]
          },
          "screen": { "type": "text_input", "id": "input", "fieldId": "amount" }
        }
        """.trimIndent()

    @Test
    fun `a rule pointing at an undeclared field is reported when the mapping is configured`() {
        val findings = run(mapOf("required_if" to "targetFieldId"))

        assertEquals(1, findings.size, findings.toString())
        assertTrue("ghost" in findings.single().message, findings.single().message)
    }

    @Test
    fun `without the mapping the same response passes — the kit does not guess a plug-in's rule names`() {
        assertTrue(run(crossReferenceKeys = emptyMap()).isEmpty())
    }

    private fun run(crossReferenceKeys: Map<String, String>): List<TckFinding> =
        runBlocking {
            TckRunner(
                StubTransport(formResponse),
                TckConfig(schemas = schemas, openApi = openApi, crossReferenceKeys = crossReferenceKeys),
            ).run()
        }.findings.filter { it.check == "form-fields" }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

// Answers every request with the same body: what is under test is a check, not a transport.
private class StubTransport(
    private val body: String,
) : TckTransport {
    override suspend fun request(
        method: String,
        path: String,
        headers: Map<String, String>,
        body: String?,
    ): TckResponse = TckResponse(status = 200, headers = emptyMap(), body = this.body)
}
