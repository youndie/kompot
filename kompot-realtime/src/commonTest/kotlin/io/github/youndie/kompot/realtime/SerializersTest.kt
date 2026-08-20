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
