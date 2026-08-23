package io.github.youndie.kompot

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@Serializable
@SerialName("probe_text")
private data class ProbeText(
    override val id: String,
    override val modifiers: List<KompotModifierNode> = emptyList(),
    val text: String,
) : KompotComponent

@Serializable
@SerialName("probe_open")
private data class ProbeOpen(
    val target: String,
) : KompotAction

private val json =
    Json {
        classDiscriminator = "type"
        serializersModule =
            kompotCoreSerializersModule +
                SerializersModule {
                    polymorphic(KompotComponent::class) { subclass(ProbeText::class) }
                    polymorphic(KompotAction::class) { subclass(ProbeOpen::class) }
                }
    }

// This test earns its place by WHERE it runs rather than by what it asserts. The same round trip
// through a reified decodeFromString passes on the JVM and throws in a browser, because the bases are
// interfaces a platform without reflection cannot resolve a serialiser for. Running in commonTest
// means every target the toolkit publishes for answers the question, including wasmJs.
class KompotJsonTest {
    @Test
    fun `a screen root round-trips on every target`() {
        val root: KompotComponent = ProbeText(id = "t", text = "Home")

        val decoded = json.decodeKompotComponent(json.encodeKompotComponent(root))

        assertEquals(root, decoded)
    }

    @Test
    fun `an action round-trips on every target`() {
        val action: KompotAction = ProbeOpen(target = "app://home")

        assertEquals(action, json.decodeKompotAction(json.encodeKompotAction(action)))
    }

    // The degradation of §2.1 travels through the same door: an unfamiliar type is a placeholder, not
    // a failure, and that has to hold where the serialiser is explicit too.
    @Test
    fun `an unfamiliar type still degrades rather than throwing`() {
        val decoded = json.decodeKompotComponent("""{"type":"nonesuch","id":"x"}""")

        assertIs<UnknownComponent>(decoded)
        assertEquals("nonesuch", decoded.originalType)
    }
}
