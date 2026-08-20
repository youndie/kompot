package io.github.youndie.kompot.standard

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotModifierNode
import io.github.youndie.kompot.TypographyToken
import io.github.youndie.kompot.registry.KompotComponentMarker

@Serializable
@SerialName("column")
@KompotComponentMarker
data class ColumnComponent(
    override val id: String,
    override val modifiers: List<KompotModifierNode> = emptyList(),
    val children: List<@Polymorphic KompotComponent>,
    val spacing: Int = 0,
) : KompotComponent

// A horizontal container — a pair of fields side by side, say a document number and its date. A
// child's share of the width is set by a KompotModifierNode.Weight node in the child's own
// modifiers rather than by a property here, the same trick as Compose's RowScope.weight.
@Serializable
@SerialName("row")
@KompotComponentMarker
data class RowComponent(
    override val id: String,
    override val modifiers: List<KompotModifierNode> = emptyList(),
    val children: List<@Polymorphic KompotComponent>,
    val spacing: Int = 0,
) : KompotComponent

@Serializable
@SerialName("text")
@KompotComponentMarker
data class TextComponent(
    override val id: String,
    override val modifiers: List<KompotModifierNode> = emptyList(),
    val text: String,
    // null means "no style set explicitly": the renderer decides what that looks like by default.
    // kompot-standard must not assume the client uses Material3 or its type scale at all.
    val style: TypographyToken? = null,
) : KompotComponent

@Serializable
@SerialName("button")
@KompotComponentMarker
data class ButtonComponent(
    override val id: String,
    override val modifiers: List<KompotModifierNode> = emptyList(),
    val text: String,
    val action: @Polymorphic KompotAction,
) : KompotComponent

// One row of a simple table grid (see TableComponent); cells in a row always share the width
// equally. `header` is a visual accent only and does not affect the data.
@Serializable
data class TableRow(
    val cells: List<String>,
    val header: Boolean = false,
)

// A simple table grid — deliberately without per-cell styles or colspan, to keep the contract flat.
// Anything more elaborate is built from row{} and column{}.
@Serializable
@SerialName("table")
@KompotComponentMarker
data class TableComponent(
    override val id: String,
    override val modifiers: List<KompotModifierNode> = emptyList(),
    val rows: List<TableRow>,
) : KompotComponent

@Serializable
@SerialName("navigate")
data class NavigateAction(
    val deeplink: String,
) : KompotAction

@Serializable
@SerialName("copy_text")
data class CopyTextAction(
    val text: String,
) : KompotAction

// A terminal client-side action: close the current overlay, dialog or full-screen banner. Unlike
// NavigateAction it goes to no screen; it signals "hide what you showed last", and the concrete
// screen on the client decides what that means.
@Serializable
@SerialName("close")
data object CloseAction : KompotAction
