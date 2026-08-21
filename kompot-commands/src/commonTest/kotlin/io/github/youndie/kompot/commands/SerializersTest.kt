package io.github.youndie.kompot.commands

import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.form.standard.TextValue
import io.github.youndie.kompot.form.standard.formStandardSerializersModule
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The payload's value types are NOT registered by this module: it owns the action, form-standard owns
// text_value and its siblings. Composing the two modules here is the same thing an application does,
// and it is what the tests below are really checking — the action is useless without a value plug-in.
private val json =
    Json {
        classDiscriminator = "type"
        serializersModule = kompotCommandsSerializersModule + formStandardSerializersModule
    }

class SerializersTest {
    @Test
    fun `PerformAction round-trips through the open KompotAction type`() {
        val action =
            PerformAction(
                url = "/tasks/move",
                payload = mapOf("taskId" to TextValue("T-42"), "status" to TextValue("in_review")),
            )

        val encoded = json.encodeToString(PolymorphicSerializer(KompotAction::class), action)
        val decoded = json.decodeFromString(PolymorphicSerializer(KompotAction::class), encoded)

        assertEquals(action, decoded)
    }

    @Test
    fun `the payload values carry their own discriminator`() {
        val action = PerformAction(url = "/tasks/move", payload = mapOf("taskId" to TextValue("T-42")))

        val encoded = json.encodeToString(PolymorphicSerializer(KompotAction::class), action)

        assertTrue(encoded.contains("\"type\":\"perform\""), encoded)
        assertTrue(encoded.contains("\"type\":\"text_value\""), encoded)
    }

    // What separates one item of a list from another is the payload, and nothing else: two buttons on
    // two cards differ in it alone. Were the payload dropped from the wire when it happens to be
    // empty-by-default, the distinction would be invisible in exactly the case that matters.
    @Test
    fun `two items differ only by their payload`() {
        val first = PerformAction(url = "/tasks/move", payload = mapOf("taskId" to TextValue("T-1")))
        val second = PerformAction(url = "/tasks/move", payload = mapOf("taskId" to TextValue("T-2")))

        val encodedFirst = json.encodeToString(PolymorphicSerializer(KompotAction::class), first)
        val encodedSecond = json.encodeToString(PolymorphicSerializer(KompotAction::class), second)

        assertTrue(encodedFirst != encodedSecond)
        assertEquals(first, json.decodeFromString(PolymorphicSerializer(KompotAction::class), encodedFirst))
        assertEquals(second, json.decodeFromString(PolymorphicSerializer(KompotAction::class), encodedSecond))
    }

    @Test
    fun `an action with no payload is still a valid action`() {
        val action = PerformAction(url = "/session/refresh")

        val encoded = json.encodeToString(PolymorphicSerializer(KompotAction::class), action)

        assertEquals(action, json.decodeFromString(PolymorphicSerializer(KompotAction::class), encoded))
    }
}
