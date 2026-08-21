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

    // Two ways to state an extent, and they are not redundant: SizeType is symbolic and resolved
    // against the parent (Fill takes what is offered, Wrap takes what the content needs), while the
    // dp fields are absolute. A symbolic value and a number on the same axis contradict each other,
    // so the number wins and the symbol is ignored — see toComposeModifier. Both fields are optional
    // and default to null, which is what keeps this addition compatible under SPEC.md §15: a client
    // built before them reads the node it already understood and drops the keys it does not.
    @Serializable
    @SerialName("size")
    data class Size(
        val width: SizeType? = null,
        val height: SizeType? = null,
        val widthDp: Int? = null,
        val heightDp: Int? = null,
    ) : KompotModifierNode

    // A scope modifier: meaningful only inside a RowScope/ColumnScope parent, so the general mapper
    // deliberately ignores it — the parent renderer extracts and applies it itself.
    @Serializable
    @SerialName("weight")
    data class Weight(
        val value: Float,
    ) : KompotModifierNode
}
