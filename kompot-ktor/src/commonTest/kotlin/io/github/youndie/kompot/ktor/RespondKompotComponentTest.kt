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
import kotlinx.serialization.modules.plus
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotModifierNode
import io.github.youndie.kompot.kompotCoreSerializersModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Serializable
@SerialName("test_component")
private data class TestComponent(
    override val id: String,
    override val modifiers: List<KompotModifierNode> = emptyList(),
    val label: String,
) : KompotComponent

private val json =
    Json {
        classDiscriminator = "type"
        serializersModule =
            kompotCoreSerializersModule +
            SerializersModule {
                polymorphic(KompotComponent::class) {
                    subclass(TestComponent::class)
                }
            }
    }

// A regression test for the very bug this helper exists to fix: a plain call.respond(component)
// resolves the serialiser from the concrete runtime class through ContentNegotiation's reflective
// TypeInfo, which drops the "type" discriminator on the ROOT of the tree, and the client receives an
// UnknownComponent instead of the real one.
class RespondKompotComponentTest {
    @Test
    fun `the root component keeps its type discriminator in the response body`() =
        testApplication {
            routing {
                get("/component") {
                    call.respondKompotComponent(json, TestComponent(id = "root", label = "hello"))
                }
            }

            val response = client.get("/component")
            val body = response.bodyAsText()

            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(body.contains("\"type\":\"test_component\""), "expected a type discriminator at the root, got: $body")
            assertTrue(body.contains("\"id\":\"root\""))
            assertTrue(body.contains("\"label\":\"hello\""))
        }

    @Test
    fun `the response can be decoded back into the same component through the open KompotComponent type`() =
        testApplication {
            routing {
                get("/component") {
                    call.respondKompotComponent(json, TestComponent(id = "root", label = "hello"))
                }
            }

            val body = client.get("/component").bodyAsText()
            val decoded = json.decodeFromString<KompotComponent>(body)

            assertEquals(TestComponent(id = "root", label = "hello"), decoded)
        }

    @Test
    fun `content type is set to application json`() =
        testApplication {
            routing {
                get("/component") {
                    call.respondKompotComponent(json, TestComponent(id = "root", label = "hello"))
                }
            }

            val response = client.get("/component")

            assertTrue(response.headers["Content-Type"]?.startsWith("application/json") == true)
        }
}
