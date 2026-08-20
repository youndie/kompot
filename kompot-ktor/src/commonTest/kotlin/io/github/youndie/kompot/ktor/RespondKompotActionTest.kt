package io.github.youndie.kompot.ktor

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import io.github.youndie.kompot.KompotAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Serializable
@SerialName("test_action")
private data class TestAction(
    val value: String,
) : KompotAction

private val json =
    Json {
        classDiscriminator = "type"
        serializersModule =
            SerializersModule {
                polymorphic(KompotAction::class) {
                    subclass(TestAction::class)
                }
            }
    }

// A regression test for the same bug as RespondKompotComponentTest: a plain call.respond(action)
// resolves the serialiser from the concrete runtime class and loses the root's "type" discriminator.
class RespondKompotActionTest {
    @Test
    fun `the root action keeps its type discriminator in the response body`() =
        testApplication {
            routing {
                get("/action") {
                    call.respondKompotAction(json, TestAction(value = "hello"))
                }
            }

            val response = client.get("/action")
            val body = response.bodyAsText()

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(body.contains("\"type\":\"test_action\""), "expected a type discriminator at the root, got: $body")
            assertTrue(body.contains("\"value\":\"hello\""))
        }

    @Test
    fun `the response can be decoded back into the same action through the open KompotAction type`() =
        testApplication {
            routing {
                get("/action") {
                    call.respondKompotAction(json, TestAction(value = "hello"))
                }
            }

            val body = client.get("/action").bodyAsText()
            val decoded = json.decodeFromString<KompotAction>(body)

            assertEquals(TestAction(value = "hello"), decoded)
        }

    @Test
    fun `content type is set to application json`() =
        testApplication {
            routing {
                get("/action") {
                    call.respondKompotAction(json, TestAction(value = "hello"))
                }
            }

            val response = client.get("/action")

            assertTrue(response.headers["Content-Type"]?.startsWith("application/json") == true)
        }
}
