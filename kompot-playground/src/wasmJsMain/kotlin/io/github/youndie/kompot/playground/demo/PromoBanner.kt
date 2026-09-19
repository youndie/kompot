package io.github.youndie.kompot.playground.demo

import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotModifierNode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

// A COMPONENT THE TOOLKIT DOES NOT HAVE, written the way a deployment writes one: a @Serializable
// class with a wire name, a line in a serializers module, a renderer in a map. Nothing here is
// privileged — this file plus its renderer is the whole integration, and that is the second thing
// the page shows for free.
//
// It exists because the showcase needs a type an older client could not know. Borrowing one from a
// consumer's product was not an option — their names do not belong in an open repository — and
// inventing a broken type would demonstrate a bug rather than the mechanism.
//
// Three fields and a call to action on purpose: the more there is to lose, the clearer what an old
// client loses. A text node in its place would prove nothing.
@Serializable
@SerialName("promo_banner")
public data class PromoBanner(
    override val id: String = "promo",
    override val modifiers: List<KompotModifierNode> = emptyList(),
    val title: String,
    val text: String,
    val cta: String,
) : KompotComponent

// What a deployment merges into kompotJson(applicationModule = …). The toolkit's own module knows
// nothing about this type, which is exactly the state an older client is in.
public val demoSerializersModule: SerializersModule =
    SerializersModule {
        polymorphic(KompotComponent::class) { subclass(PromoBanner::class) }
    }
