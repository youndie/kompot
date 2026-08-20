package io.github.youndie.kompot.ktor

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.PolymorphicSerializer
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
private data class ReceiveTestComponent(
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
                    subclass(ReceiveTestComponent::class)
                }
            }
    }

// The mirror of RespondKompotComponentTest: a plain call.receive<KompotComponent>() cannot resolve a
// concrete runtime class's serialiser for an open interface, so this checks that
// receiveKompotComponent decodes the body correctly through the "type" discriminator.
class ReceiveKompotComponentTest {
    @Test
    fun `the request body is decoded back into the concrete component through the open KompotComponent type`() =
        testApplication {
            lateinit var received: KompotComponent
            routing {
                post("/component") {
                    received = call.receiveKompotComponent(json)
                    call.respondKompotComponent(json, received)
                }
            }

            val response =
                client.post("/component") {
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(PolymorphicSerializer(KompotComponent::class), ReceiveTestComponent(id = "root", label = "hello")))
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ReceiveTestComponent(id = "root", label = "hello"), received)
            assertTrue(response.bodyAsText().contains("\"type\":\"test_component\""))
        }
}
