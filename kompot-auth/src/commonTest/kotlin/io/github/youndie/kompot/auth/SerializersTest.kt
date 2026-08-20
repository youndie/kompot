package io.github.youndie.kompot.auth

import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import io.github.youndie.kompot.KompotAction
import kotlin.test.Test
import kotlin.test.assertEquals

private val json =
    Json {
        classDiscriminator = "type"
        serializersModule = kompotAuthSerializersModule
    }

class SerializersTest {
    @Test
    fun `UpdateSessionAction round-trips through the open KompotAction type`() {
        val action = UpdateSessionAction(accessToken = "at", refreshToken = "rt")

        val encoded = json.encodeToString(PolymorphicSerializer(KompotAction::class), action)
        val decoded = json.decodeFromString(PolymorphicSerializer(KompotAction::class), encoded)

        assertEquals(action, decoded)
    }

    @Test
    fun `the type discriminator is present in the encoded JSON`() {
        val action = UpdateSessionAction(accessToken = "at", refreshToken = "rt")

        val encoded = json.encodeToString(PolymorphicSerializer(KompotAction::class), action)

        assertEquals(true, encoded.contains("\"type\":\"update_session\""))
    }
}
