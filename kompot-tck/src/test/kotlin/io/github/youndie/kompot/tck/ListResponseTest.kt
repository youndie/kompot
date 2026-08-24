package io.github.youndie.kompot.tck

import io.github.youndie.kompot.spec.KompotSpecResources
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// A data source answers a list, and the kit unwrapped that list down to the schema of one element and
// then validated the whole body against it. The two can never agree, so every conformant data source
// was reported as broken — by a finding naming the server, which was answering exactly what it
// declared.
//
// Nothing in these tests declared an array response before, which is where the missing half was
// missing from: the kit and its tests shared one blind spot.
class ListResponseTest {
    private val schemas = KompotSpecResources(root = "kompot-spec").schemas()

    private val openApi =
        Json.decodeFromString(
            JsonObject.serializer(),
            """
            {
              "paths": {
                "/sources/accounts": {
                  "get": {
                    "x-kompot-endpoint-kind": "data_source",
                    "responses": {
                      "200": {
                        "content": {
                          "application/json": {
                            "schema": {
                              "type": "array",
                              "items": { "${'$'}ref": "kompot.profile.schema.json#/${'$'}defs/FieldValue" }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
            """.trimIndent(),
        )

    private fun schemaFindings(answer: String): List<TckFinding> =
        runBlocking {
            TckRunner(
                object : TckTransport {
                    override suspend fun request(
                        method: String,
                        path: String,
                        headers: Map<String, String>,
                        body: String?,
                    ): TckResponse = TckResponse(status = 200, headers = emptyMap(), body = answer)
                },
                TckConfig(schemas = schemas, openApi = openApi),
            ).run()
        }.findings.filter { it.check == "schema" }

    private val account = """{"type":"entity_value","id":"acc-1","title":"Current account"}"""

    @Test
    fun `a correct list is not a finding`() {
        assertEquals(emptyList(), schemaFindings("[$account,$account]"))
    }

    // The point of validating element by element rather than as a whole: the finding is an address a
    // person can look at, not a verdict on the endpoint.
    @Test
    fun `a list with one bad element produces one finding naming that element`() {
        val findings = schemaFindings("""[$account,{"id":"acc-2","title":"Savings"}]""")

        assertEquals(1, findings.size, findings.toString())
        assertTrue(findings.single().message.startsWith("[1]"), findings.single().message)
    }

    // Otherwise this reports nothing at all: there are no items to walk, and "no findings" is what a
    // correct list looks like too.
    @Test
    fun `a body that is not an array where a list was declared is a finding`() {
        val findings = schemaFindings(account)

        assertEquals(1, findings.size, findings.toString())
        assertTrue(findings.single().message.contains("not an array"), findings.single().message)
    }
}
