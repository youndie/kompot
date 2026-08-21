package io.github.youndie.kompot.tck

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Four cases, and the reading that shipped got two of them wrong. All four come straight from the
// OpenAPI rules for `security`: an operation without the key inherits the document's, and an empty
// array on the operation overrides it with "nothing required".
class EndpointSecurityTest {
    private fun endpoints(document: String): Map<String, TckEndpoint> =
        TckEndpoints
            .fromOpenApi(Json.decodeFromString(JsonObject.serializer(), document))
            .associateBy { it.path }

    private val underASecuredDocument =
        endpoints(
            """
            {
              "security": [{ "bearerAuth": [] }],
              "paths": {
                "/inherits":   { "get": { "responses": { "200": { "content": { "application/json": {} } } } } },
                "/public":     { "get": { "security": [], "responses": { "200": { "content": { "application/json": {} } } } } },
                "/own-scheme": { "get": { "security": [{ "bearerAuth": [] }], "responses": { "200": { "content": { "application/json": {} } } } } }
              }
            }
            """.trimIndent(),
        )

    // The finding from the report: a login form declares `security: []` because a person with no
    // session has to be able to fetch the screen carrying the password field. It was read as secured
    // and reported for answering 200.
    @Test
    fun `an empty security array declares the operation public`() {
        assertFalse(underASecuredDocument.getValue("/public").secured)
    }

    @Test
    fun `an operation with no security of its own inherits the document's`() {
        assertTrue(underASecuredDocument.getValue("/inherits").secured)
    }

    @Test
    fun `an operation naming a scheme is secured`() {
        assertTrue(underASecuredDocument.getValue("/own-scheme").secured)
    }

    @Test
    fun `nothing is secured when neither the document nor the operation requires anything`() {
        val open =
            endpoints(
                """
                {
                  "paths": {
                    "/anything": { "get": { "responses": { "200": { "content": { "application/json": {} } } } } }
                  }
                }
                """.trimIndent(),
            )

        assertEquals(false, open.getValue("/anything").secured)
    }
}
