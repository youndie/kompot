package io.github.youndie.kompot

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

val kompotCoreSerializersModule =
    SerializersModule {
        polymorphic(KompotComponent::class) {
            defaultDeserializer { className -> UnknownComponentSerializer(className ?: "unknown") }
        }
        polymorphic(KompotAction::class) {
            defaultDeserializer { className -> UnknownActionSerializer(className ?: "unknown") }
        }
    }

@Serializable
data class UnknownComponent(
    override val id: String = "unknown",
    override val modifiers: List<KompotModifierNode> = emptyList(),
    val originalType: String = "unknown",
) : KompotComponent

@Serializable
data class UnknownAction(
    val originalType: String = "unknown",
) : KompotAction

private class UnknownComponentSerializer(
    private val originalType: String,
) : KSerializer<UnknownComponent> {
    private val delegate = UnknownComponent.serializer()
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(
        encoder: Encoder,
        value: UnknownComponent,
    ) = delegate.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): UnknownComponent = delegate.deserialize(decoder).copy(originalType = originalType)
}

private class UnknownActionSerializer(
    private val originalType: String,
) : KSerializer<UnknownAction> {
    private val delegate = UnknownAction.serializer()
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(
        encoder: Encoder,
        value: UnknownAction,
    ) = delegate.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): UnknownAction = delegate.deserialize(decoder).copy(originalType = originalType)
}
