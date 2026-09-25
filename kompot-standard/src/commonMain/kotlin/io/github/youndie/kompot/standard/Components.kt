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
    // Where the children sit ACROSS the stack — left, centre or right of a column. Fields rather than
    // a modifier for the reason `action` is one (SPEC.md §4.7): an unknown field is ignored and the
    // stack is laid out as before, an unknown modifier node fails the whole parse. Open strings, like
    // a button's variant: a word this client does not know means the default.
    /** Where the children sit across the column: `start` (default), `center` or `end`. An unfamiliar word means `start`. */
    val alignment: String? = null,
    /**
     * How the children share the column's height: `start` (default), `center`, `end`,
     * `space_between`, `space_around` or `space_evenly`. `spacing` stays the smallest gap. An unfamiliar word means `start`.
     */
    val arrangement: String? = null,
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
    // See ColumnComponent.alignment and .arrangement; the axes turn with the container.
    /** Where the children sit across the row — top, middle or bottom: `start` (default), `center` or `end`. An unfamiliar word means `start`. */
    val alignment: String? = null,
    /**
     * How the children share the row's width: `start` (default), `center`, `end`, `space_between`,
     * `space_around` or `space_evenly`. `spacing` stays the smallest gap. An unfamiliar word means `start`.
     */
    val arrangement: String? = null,
    // A rail of cards: the row keeps its width and its content scrolls sideways. A field rather than a
    // type of its own (SPEC.md §4.9): a client that predates it ignores the field and draws the row it
    // always drew — still, with the tail cut off at the edge — where an unknown type would be a hole.
    /** Whether the row scrolls sideways when its children are wider than it. `weight` means nothing in a row that scrolls. */
    val scrollable: Boolean = false,
) : KompotComponent

/**
 * Nodes laid over one another, in order: the first is the bottom layer. The box is as large as its
 * children that do not fill it; a child whose size is `Fill` on an axis takes the box's extent there.
 */
@Serializable
@SerialName("box")
@KompotComponentMarker
public data class BoxComponent(
    override val id: String,
    override val modifiers: List<KompotModifierNode> = emptyList(),
    val children: List<@Polymorphic KompotComponent>,
    // One alignment for every layer, not one per child. Per child would need either a modifier —
    // KompotModifierNode is closed (SPEC.md §2.3), so an unknown one fails the parse of the whole
    // response — or a wrapper object around each child, which every walker of the tree would have to
    // learn. A layer that sits elsewhere is a nested box that fills this one and aligns its own child
    // (SPEC.md §4.8): the same words, no new mechanism.
    /**
     * Where the layers sit in the box: `top_start` (default), `top_center`, `top_end`, `center_start`,
     * `center`, `center_end`, `bottom_start`, `bottom_center` or `bottom_end`. An unfamiliar word means `top_start`.
     */
    val alignment: String? = null,
) : KompotComponent

/**
 * A thin rule between neighbours, drawn across its parent's axis: horizontal in a column, vertical in
 * a row. Its colour is the design system's unless the server names a token.
 */
@Serializable
@SerialName("divider")
@KompotComponentMarker
public data class DividerComponent(
    override val id: String,
    override val modifiers: List<KompotModifierNode> = emptyList(),
    // A token, as on text: the protocol never carries a colour value (SPEC.md §6).
    /** The rule's colour; the design system's own when absent. */
    val color: ColorToken? = null,
) : KompotComponent

/**
 * Empty room along its parent's axis: [size] dp of height in a column, of width in a row. A `weight`
 * modifier makes it take its share instead — what pushes a button to the bottom of a fixed screen.
 */
@Serializable
@SerialName("spacer")
@KompotComponentMarker
public data class SpacerComponent(
    override val id: String,
    override val modifiers: List<KompotModifierNode> = emptyList(),
    // Before it, a gap that was not `spacing` was an empty column with a size — a node with an id
    // somebody had to invent, which reads in the tree as "something is here" (SPEC.md §4.10).
    /** The room it takes along the parent's axis, in density-independent pixels. */
    val size: Int = 0,
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

// A short message in answer to what was just done — "copied", "saved", "could not reach the server".
// An action rather than a component: the message does not live in the tree, has no id and no place
// among the nodes; it is something that HAPPENS, and it is usually the answer to another action (a
// `perform` answers with it, SPEC.md §16.4). The client draws it with its own design system — a
// snackbar, a toast, a banner — because what a message looks like is the client's (§6).
@Serializable
@SerialName("show_message")
public data class ShowMessageAction(
    /** The words to show. */
    val text: String,
    /** How serious it is: `info` (default) or `error`. An open string; an unfamiliar word means `info`. */
    val level: String? = null,
    /** The words on the message's own button, e.g. "Undo". The button is shown only with an `action`. */
    val actionLabel: String? = null,
    /** What the message's button does. Shown only with an `actionLabel`. */
    val action: @Polymorphic KompotAction? = null,
) : KompotAction

// A tree shown over the screen — a dialog or a sheet — rather than as a screen of its own. Before it,
// every choice over a screen (the actions on an item, a filter, a short form) was a navigation that
// lost the screen underneath. One layer deep: presenting while one is open replaces it, and `close`
// closes it (SPEC.md §12.5).
@Serializable
@SerialName("present")
public data class PresentAction(
    /** The tree to show over the screen. */
    val content: @Polymorphic KompotComponent,
    /** `dialog` (default) or `sheet`. An open string; an unfamiliar word means `dialog`. */
    val kind: String? = null,
) : KompotAction

public object PresentKind {
    public const val DIALOG: String = "dialog"
    public const val SHEET: String = "sheet"
}

// "Are you sure?" before an action. A wrapper rather than a field on the action it guards, and the
// reason is the client that predates it (SPEC.md §12.5): a field it would ignore (§3) and run the
// guarded action WITHOUT asking — a delete on the first tap — while an unknown action it does not run
// at all. Failing closed is the only acceptable way for a question about a destructive action to fail.
@Serializable
@SerialName("confirm")
public data class ConfirmAction(
    /** The question, e.g. "Delete the board?". */
    val question: String,
    /** The action to run once the person agrees. */
    val action: @Polymorphic KompotAction,
    /** More words under the question, e.g. what cannot be undone. */
    val detail: String? = null,
    /** The words on the agreeing button; the client's own when absent. */
    val confirmLabel: String? = null,
    /** The words on the refusing button; the client's own when absent. */
    val cancelLabel: String? = null,
) : KompotAction

public object MessageLevel {
    public const val INFO: String = "info"
    public const val ERROR: String = "error"
}

// The words `alignment` and `arrangement` take, as constants for whoever writes a tree in Kotlin. The
// wire keeps them open strings (SPEC.md §4.7): these are the ones this toolkit's client understands,
// not a closed set a newer server is held to.
public object StackAlignment {
    public const val START: String = "start"
    public const val CENTER: String = "center"
    public const val END: String = "end"
}

public object StackArrangement {
    public const val START: String = "start"
    public const val CENTER: String = "center"
    public const val END: String = "end"
    public const val SPACE_BETWEEN: String = "space_between"
    public const val SPACE_AROUND: String = "space_around"
    public const val SPACE_EVENLY: String = "space_evenly"
}

// The words a box's `alignment` takes (SPEC.md §4.8): the two axes of StackAlignment, vertical first.
public object BoxAlignment {
    public const val TOP_START: String = "top_start"
    public const val TOP_CENTER: String = "top_center"
    public const val TOP_END: String = "top_end"
    public const val CENTER_START: String = "center_start"
    public const val CENTER: String = "center"
    public const val CENTER_END: String = "center_end"
    public const val BOTTOM_START: String = "bottom_start"
    public const val BOTTOM_CENTER: String = "bottom_center"
    public const val BOTTOM_END: String = "bottom_end"
}
