package io.github.youndie.kompot

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Polymorphic
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

// The placeholder a client produces for a type it does not know — and the one place a server's
// suggested equivalent can be read. A deployment that replaces a toolkit component with one of its own
// knows the exact stand-in (a text_input where its multiline box would be) and had no way to say so;
// §2.1 left the substitution entirely to the client, which knows only that something was unfamiliar.
//
// `fallback` lives here rather than on the KompotComponent base for a reason worth keeping: it is only
// ever consulted when the type is UNKNOWN, so only the unknown path has to be able to read it. On the
// base it would have cost a property on all seventeen component types of the toolkit, plus one on
// every plug-in type anybody else writes — and a type that forgot it would accept a fallback in Kotlin
// and drop it in transit, because kotlinx.serialization writes what a concrete class declares.
//
// A client that DOES know the type never sees the key: an unknown field is ignored by §3.
@Serializable
data class UnknownComponent(
    override val id: String = "unknown",
    override val modifiers: List<KompotModifierNode> = emptyList(),
    val originalType: String = "unknown",
    val fallback: @Polymorphic KompotComponent? = null,
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
