package io.github.youndie.kompot.standard

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import io.github.youndie.kompot.ColorToken
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotModifierNode
import io.github.youndie.kompot.TypographyToken
import io.github.youndie.kompot.registry.KompotComponentMarker

/** A vertical stack of nodes. The root of most screens, and the only container that scrolls. */
@Serializable
@SerialName("column")
@KompotComponentMarker
public data class ColumnComponent(
    override val id: String,
    override val modifiers: List<KompotModifierNode> = emptyList(),
    val children: List<@Polymorphic KompotComponent>,
    /** The gap between children, in density-independent pixels. */
    val spacing: Int = 0,
    // What tapping the container does. Before it existed only `button` carried an action, so a list
    // whose rows open something was expressible only as a list of buttons — the protocol deciding a
    // layout the product should have decided. It is a field here rather than a modifier node because
    // KompotModifierNode is CLOSED (SPEC.md §2.3): an unknown node fails the parse of the whole
    // response, so a "clickable" modifier would take the screen down on a client that predates it,
    // while an unknown FIELD is simply ignored and the row renders exactly as before, merely not
    // tappable.
    val action: @Polymorphic KompotAction? = null,
) : KompotComponent

// A horizontal container — a pair of fields side by side, say a document number and its date. A
// child's share of the width is set by a KompotModifierNode.Weight node in the child's own
// modifiers rather than by a property here, the same trick as Compose's RowScope.weight.
/** A horizontal row of nodes. Unlike a column it never scrolls: a row is one item of its parent. */
@Serializable
@SerialName("row")
@KompotComponentMarker
public data class RowComponent(
    override val id: String,
    override val modifiers: List<KompotModifierNode> = emptyList(),
    val children: List<@Polymorphic KompotComponent>,
    /** The gap between children, in density-independent pixels. */
    val spacing: Int = 0,
    // See ColumnComponent.action.
    val action: @Polymorphic KompotAction? = null,
) : KompotComponent

/** A run of words to show. The only node that carries copy, and every string a person reads is one. */
@Serializable
@SerialName("text")
@KompotComponentMarker
public data class TextComponent(
    override val id: String,
    override val modifiers: List<KompotModifierNode> = emptyList(),
    val text: String,
    // null means "no style set explicitly": the renderer decides what that looks like by default.
    // kompot-standard must not assume the client uses Material3 or its type scale at all.
    val style: TypographyToken? = null,
    // The colour of the letters, which for a long time nothing on the wire could say: a ColorToken
    // reached only `background` and `gradient`, which paint behind the content, so the only way to
    // colour a word was a typography token that happens to carry a colour — turning "the same body
    // text, but red" into a second entry in the design system's type scale.
    //
    // A token rather than a value, like every other colour here: the server names a role and the
    // client's design system decides what it looks like, so a deployment cannot paint an unreadable
    // screen from the backend. Unknown token falls back the way §6 requires.
    //
    // null keeps the resolution that was there before, and the order matters (§6): this token, then
    // the colour of the typography token, then the colour of the surface the text sits on.
    val color: ColorToken? = null,
    // The same text, cut into runs that can differ. Absent means the node is uniform, which is what it
    // always was.
    //
    // `text` above stays the whole string and remains authoritative: a client that knows nothing of
    // spans draws it flat and loses styling rather than content. That is why this could be added at
    // all — the alternative, a component of its own, would have degraded to a placeholder and taken
    // the words with it.
    //
    // Hence the rule in §14: `text` MUST equal the concatenation of the spans. Two places holding one
    // string is the shape that drifts, so the conformance kit checks it rather than trusting it.
    val spans: List<TextSpan> = emptyList(),
    // What becomes of a string that does not fit. §14 makes the server the only party allowed to
    // produce text, so shortening one is its job too — but it knows neither the screen's width nor the
    // font, and had no way to say what should happen instead. null keeps the previous behaviour: as
    // many lines as the text needs.
    //
    // Two plain fields rather than an overflow vocabulary, deliberately: a closed set of names would
    // gain a value one day and take down the whole screen of every client released before it, since an
    // unknown enum constant fails the parse rather than falling back.
    /** How many lines the text may occupy before it is cut. Null lets it take as many as it needs. */
    val maxLines: Int? = null,
    // Only meaningful together with maxLines: whether the cut is marked. false clips silently.
    /** Whether a cut is marked with an ellipsis. Only meaningful together with maxLines. */
    val ellipsis: Boolean = true,
) : KompotComponent

// One run of a text node: its own words, optionally its own style, optionally something to do. A span
// carrying an action is what makes a link inside a sentence possible — the thing a row of text nodes
// cannot be, because a row does not wrap and the first long sentence leaves the screen.
@Serializable
public data class TextSpan(
    val text: String,
    val style: TypographyToken? = null,
    // One word in the sentence's colour and not the sentence's: an amount in red, a warning inside a
    // paragraph. Spans carry no modifiers, so before this there was no way to colour a run at all —
    // not even the expensive one of giving it a background.
    //
    // Resolved exactly like the node's own colour and independently of it: a span that names none
    // takes the node's, which takes the typography token's, which takes the surface's.
    val color: ColorToken? = null,
    val action: @Polymorphic KompotAction? = null,
)

/** A control that raises an action when pressed. */
@Serializable
@SerialName("button")
@KompotComponentMarker
public data class ButtonComponent(
    override val id: String,
    override val modifiers: List<KompotModifierNode> = emptyList(),
    val text: String,
    val action: @Polymorphic KompotAction,
    // Which button matters. Emphasis is content rather than theme — whoever wrote the screen decides
    // that "Cancel" is quiet and "Submit" is not — and there was nowhere to put it, so a deployment
    // had to signal it some other way. Inferring it from the presence of a background modifier works
    // and is a guess; a server that does not share the guess draws the two alike.
    //
    // An open string, named by the design system exactly as a colour token is: the protocol fixes no
    // set of emphases, and a client that does not recognise one falls back to its ordinary button.
    /** Which of the client's button styles to use. An unfamiliar word draws the neutral one. */
    val variant: String? = null,
) : KompotComponent

// One row of a simple table grid (see TableComponent); cells in a row always share the width
// equally. `header` is a visual accent only and does not affect the data.
@Serializable
public data class TableRow(
    val cells: List<String>,
    val header: Boolean = false,
)

// A simple table grid — deliberately without per-cell styles or colspan, to keep the contract flat.
// Anything more elaborate is built from row{} and column{}.
@Serializable
@SerialName("table")
@KompotComponentMarker
public data class TableComponent(
    override val id: String,
    override val modifiers: List<KompotModifierNode> = emptyList(),
    val rows: List<TableRow>,
) : KompotComponent

@Serializable
@SerialName("navigate")
public data class NavigateAction(
    val deeplink: String,
) : KompotAction

// Leaving the application, on purpose and visibly. navigate cannot do it and must not: its deeplink
// forbids http and https precisely so that a server cannot walk somebody out to a web page through an
// ordinary transition (SPEC.md §12.2). That ban stays — this is a different door, and it is marked.
//
// The separation is the whole design. A client can treat one action as "goes somewhere inside" and
// this one as "leaves", and can put a confirmation, an allowlist or nothing at all in front of it;
// with a flag on navigate the two would have been indistinguishable at the point where it matters.
//
// The toolkit opens nothing itself: like every other action this one is handed to the application's
// KompotActionHandler, which knows what "open" means where it runs.
@Serializable
@SerialName("open_url")
public data class OpenUrlAction(
    val url: String,
) : KompotAction

@Serializable
@SerialName("copy_text")
public data class CopyTextAction(
    val text: String,
) : KompotAction

// A terminal client-side action: close the current overlay, dialog or full-screen banner. Unlike
// NavigateAction it goes to no screen; it signals "hide what you showed last", and the concrete
// screen on the client decides what that means.
@Serializable
@SerialName("close")
public data object CloseAction : KompotAction
