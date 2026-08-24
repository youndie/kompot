package io.github.youndie.kompot.realtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.UnknownComponent
import kotlin.test.Test
import kotlin.test.assertEquals

private val json =
    Json {
        classDiscriminator = "type"
        // kompot-realtime itself knows no concrete KompotComponent (see Messages.kt), so
        // UnknownComponent stands in for "any component" in this round-trip test. It is registered
        // here rather than in kompotCoreSerializersModule, where UnknownComponent exists only as the
        // decode fallback for UNREGISTERED types, not as an ordinary encodable component.
        serializersModule =
            SerializersModule {
                polymorphic(KompotComponent::class) {
                    subclass(UnknownComponent::class)
                }
            }
    }

// UpdateComponentMessage is no longer a sealed variant of a socket-message wrapper (that went away
// together with SubscribeMessage: SSE is one-way and the client has nothing to send after the
// handshake, see Messages.kt). It is the only frame type, so it serialises and deserialises directly
// through its own serialiser, with no PolymorphicSerializer on top.
class SerializersTest {
    @Test
    fun `UpdateComponentMessage round-trips including its polymorphic component payload`() {
        val message =
            UpdateComponentMessage(
                componentId = "greeting",
                component = UnknownComponent(id = "greeting", originalType = "text"),
            )

        val encoded = json.encodeToString(UpdateComponentMessage.serializer(), message)
        val decoded = json.decodeFromString(UpdateComponentMessage.serializer(), encoded)

        assertEquals(message, decoded)
    }

    // The envelope a screen that is not a form travels in. Before it, a topic could only be carried by
    // KompotFormResponse, which also requires a schema — so such a screen went out as a form response
    // with a form that does not exist.
    @Test
    fun `KompotScreenResponse round-trips with its topic and its polymorphic screen`() {
        val response =
            KompotScreenResponse(
                screen = UnknownComponent(id = "sweep", originalType = "column"),
                realtimeTopic = "sweep:io.ktor",
            )

        val encoded = json.encodeToString(KompotScreenResponse.serializer(), response)

        assertEquals(response, json.decodeFromString(KompotScreenResponse.serializer(), encoded))
        assertEquals(true, encoded.contains("\"type\":"), encoded)
    }

    // The field is optional so that the envelope stays usable for a screen with nothing to say about
    // updates — but a screen that says nothing is better off as a bare tree (§10.4), and the absent
    // field must decode as absent rather than as some topic of the toolkit's choosing.
    @Test
    fun `a screen response without a topic says so rather than carrying an empty one`() {
        val response = KompotScreenResponse(screen = UnknownComponent(id = "sweep", originalType = "column"))

        val encoded = json.encodeToString(KompotScreenResponse.serializer(), response)

        assertEquals(false, encoded.contains("realtimeTopic"), encoded)
        assertEquals(null, json.decodeFromString(KompotScreenResponse.serializer(), encoded).realtimeTopic)
    }

    @Test
    fun `the nested component's type discriminator is present in the encoded JSON`() {
        val message =
            UpdateComponentMessage(
                componentId = "greeting",
                component = UnknownComponent(id = "greeting", originalType = "text"),
            )

        val encoded = json.encodeToString(UpdateComponentMessage.serializer(), message)

        assertEquals(true, encoded.contains("\"type\":"))
    }
}
