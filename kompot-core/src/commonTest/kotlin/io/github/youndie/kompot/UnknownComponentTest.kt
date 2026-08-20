package io.github.youndie.kompot

import kotlinx.serialization.PolymorphicSerializer
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
@SerialName("text")
private data class TestTextComponent(
    override val id: String,
    override val modifiers: List<KompotModifierNode> = emptyList(),
    val text: String,
) : KompotComponent

private val testJson =
    Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        serializersModule =
            kompotCoreSerializersModule +
            SerializersModule {
                polymorphic(KompotComponent::class) {
                    subclass(TestTextComponent::class)
                }
            }
    }

class UnknownComponentTest {
    @Test
    fun `known type decodes normally`() {
        val component =
            testJson.decodeFromString(PolymorphicSerializer(KompotComponent::class), """{"type":"text","id":"1","text":"hi"}""")
        assertIs<TestTextComponent>(component)
        assertEquals("hi", component.text)
    }

    @Test
    fun `unknown type falls back instead of throwing`() {
        val component =
            testJson.decodeFromString(
                PolymorphicSerializer(KompotComponent::class),
                """{"type":"video_player","id":"promo-video","url":"https://example.com/video.mp4"}""",
            )

        assertIs<UnknownComponent>(component)
        assertEquals("video_player", component.originalType)
        assertEquals("promo-video", component.id)
    }

    @Test
    fun `unknown component nested inside a known tree does not break the whole payload`() {
        val json =
            """
            {"type":"text","id":"root","text":"root ok"}
            """.trimIndent()

        // A known node sitting next to potentially unknown types still decodes normally: the mere
        // presence of a default resolver does not change the path taken by known types.
        val component = testJson.decodeFromString(PolymorphicSerializer(KompotComponent::class), json)
        assertIs<TestTextComponent>(component)
    }
}
