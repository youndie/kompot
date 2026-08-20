package io.github.youndie.kompot

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// A polymorphic chain of modifier instructions rather than a flat set of optional fields: the order
// of nodes in the list MATTERS. The client folds the chain in the order the nodes arrived from the
// backend, so the order is decided by the backend through the sequence of calls in modifierBlock
// (see KompotModifierBuilder) instead of being hardcoded in the client. classDiscriminator = "type"
// in the shared Json configuration adds the "type" field to each node on serialisation.
@Serializable
sealed interface KompotModifierNode {
    @Serializable
    @SerialName("padding")
    data class Padding(
        val all: Int? = null,
        val top: Int? = null,
        val bottom: Int? = null,
        val start: Int? = null,
        val end: Int? = null,
    ) : KompotModifierNode

    @Serializable
    @SerialName("background")
    data class Background(
        val color: ColorToken,
    ) : KompotModifierNode

    @Serializable
    @SerialName("gradient")
    data class Gradient(
        val colors: List<ColorToken>,
    ) : KompotModifierNode

    @Serializable
    @SerialName("size")
    data class Size(
        val width: SizeType? = null,
        val height: SizeType? = null,
    ) : KompotModifierNode

    // A scope modifier: meaningful only inside a RowScope/ColumnScope parent, so the general mapper
    // deliberately ignores it — the parent renderer extracts and applies it itself.
    @Serializable
    @SerialName("weight")
    data class Weight(
        val value: Float,
    ) : KompotModifierNode
}
