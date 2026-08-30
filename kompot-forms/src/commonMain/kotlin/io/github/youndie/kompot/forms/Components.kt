package io.github.youndie.kompot.forms

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotModifierNode
import io.github.youndie.kompot.registry.KompotComponentMarker

@Serializable
@SerialName("text_input")
@KompotComponentMarker
data class TextInputComponent(
    override val id: String,
    override val modifiers: List<KompotModifierNode> = emptyList(),
    val fieldId: String,
    val label: String,
    val placeholder: String? = null,
    // A visual mask, for example "+1 (###) ###-##-##". "#" is a digit placeholder.
    val mask: String? = null,
    // Upper-case the input automatically — codes and identifiers — both on screen and in the stored
    // value, not merely visually.
    val uppercase: Boolean = false,
    // Behaviour rather than geometry, and that is the point: a size modifier makes the box taller and
    // leaves it a one-line field. Nothing else in the type could say this, and a deployment that needed
    // it had to replace text_input wholesale to change one thing about it.
    val multiline: Boolean = false,
    // Mask the input with dots or asterisks (a password and the like) — visually only; the stored
    // value stays as it is.
    val secret: Boolean = false,
) : KompotComponent

@Serializable
@SerialName("amount_input")
@KompotComponentMarker
data class AmountInputComponent(
    override val id: String,
    override val modifiers: List<KompotModifierNode> = emptyList(),
    val fieldId: String,
    val label: String,
    // A unit written AFTER the number, for example "10 UZS".
    val currencySuffix: String? = null,
    // The same unit written BEFORE it — "$10", "¥10". Where the symbol goes is a property of the
    // currency rather than a style choice, and of any five a product might ship two write it first,
    // so a server filling only the suffix from its own currency table is right for some of them and
    // wrong for the rest. Nothing fails when it is wrong: the field renders, the form submits, the
    // amount is correct, and the symbol sits on the wrong side of it.
    //
    // At most one of the two is set. Both set is a server mistake rather than a state with a meaning,
    // and the SUFFIX wins — not by preference but because a client released before this field draws
    // the suffix regardless, so the rule is the one that makes old and new clients agree on one
    // payload.
    //
    // This field also decides the side of a symbol that did NOT come from it: a currency arriving in
    // the value itself — through currencyFromField or a patch — is a string with no placement of its
    // own, so the component's choice of field is what says which side it goes on.
    val currencyPrefix: String? = null,
    // When set, the renderer watches this field's EntityValue locally, with no server round trip,
    // and as soon as rawMetadata["currency"] appears there it carries the unit over to the amount.
    // Picking a source instantly changes the amount's unit, because the data is already on the
    // client.
    val currencyFromField: String? = null,
    // Whether the symbol stands away from the number. It is the third thing a currency says about how
    // it is written, after the symbol itself and the side: "$50" is closed up, "50 €" is not, and a
    // server holding a table of currencies has all three in it.
    //
    // The default is a space on either side, which is NOT the typographically right answer for a
    // symbol-first currency — nobody writes "$ 50". It is what every already-released client draws,
    // and a payload that says nothing has to look the same on all of them; a server that knows better
    // says so here.
    val currencySpaced: Boolean = true,
) : KompotComponent

@Serializable
@SerialName("checkbox_input")
@KompotComponentMarker
data class CheckboxInputComponent(
    override val id: String,
    override val modifiers: List<KompotModifierNode> = emptyList(),
    val fieldId: String,
    val label: String,
    // Which affordance a boolean wears, and it is not decoration: on both phones a switch takes
    // effect now and a checkbox takes effect on submit, so a settings screen drawn as checkboxes
    // promises the wrong thing. The state is the same either way — form-standard's boolean_value
    // carries it — and only the look and the promise differ.
    //
    // An open string, like button's variant and like a colour token: the server names a kind and the
    // client decides what it looks like. "switch" is the one word the standard renderer knows; any
    // other degrades to a checkbox rather than failing, which is what §2.1 asks of an unknown name.
    val variant: String? = null,
) : KompotComponent

@Serializable
@SerialName("autocomplete_input")
@KompotComponentMarker
data class AutocompleteInputComponent(
    override val id: String,
    override val modifiers: List<KompotModifierNode> = emptyList(),
    val fieldId: String,
    val label: String,
    // A backend data-source identifier rather than a URL: the network is resolved through
    // RemoteDataSourceResolver, and the component knows nothing about it.
    val dataSourceId: String,
    val placeholder: String? = null,
) : KompotComponent

// A choice option for a dropdown or radio group: id goes into the payload (EntityValue.id), label
// goes on screen. rawMetadata is carried into EntityValue.rawMetadata on selection — a unit or a
// capacity, say — so that other fields or a ValidationRule can read it locally, with no server round
// trip.
@Serializable
data class SelectOption(
    val id: String,
    val label: String,
    val rawMetadata: Map<String, String>? = null,
)

@Serializable
@SerialName("select_input")
@KompotComponentMarker
data class SelectInputComponent(
    override val id: String,
    override val modifiers: List<KompotModifierNode> = emptyList(),
    val fieldId: String,
    val label: String,
    val options: List<SelectOption>,
    val placeholder: String? = null,
) : KompotComponent

@Serializable
@SerialName("radio_group")
@KompotComponentMarker
data class RadioGroupComponent(
    override val id: String,
    override val modifiers: List<KompotModifierNode> = emptyList(),
    val fieldId: String,
    val label: String,
    val options: List<SelectOption>,
) : KompotComponent

// A non-editable "label plus value" field: like ordinary text, styled as an input.
//
// Unbound by default — the value arrives from the server at render time — and bound when it names a
// fieldId. That second half exists because a value the SERVER computes as the form changes had
// nowhere to live: every component a patch can reach is editable, so a running total, a fee, a
// computed date or a price was either something the person could type into, or correct once and
// stale after. Fetching a whole new response to show it loses focus and scroll on every change,
// which is what FormPatch exists to avoid.
//
// Bound means bound: the field is declared in the schema like any other, follows visibleIf, receives
// patches and travels in the payload. Only the typing is missing.
@Serializable
@SerialName("read_only_field")
@KompotComponentMarker
data class ReadOnlyFieldComponent(
    override val id: String,
    override val modifiers: List<KompotModifierNode> = emptyList(),
    val label: String,
    // What to draw before anything is bound, and what a client released before `fieldId` draws
    // always. Not a fallback for a missing value but the first paint: a bound field with nothing in
    // it yet shows this rather than an empty box.
    val value: String,
    val helperText: String? = null,
    // The field of the form this displays. Absent — today's behaviour exactly, so every existing
    // tree keeps its meaning.
    //
    // What is drawn is the value's plainValue. A server that wants a formatted string sends a
    // text_value: §14 makes the server the only party allowed to produce text, and a price with a
    // currency and a separator is text.
    val fieldId: String? = null,
) : KompotComponent

@Serializable
@SerialName("submit_form")
data class SubmitFormAction(
    val formId: String,
) : KompotAction

// The words a server may send as a checkbox_input variant that the standard renderer acts on. Here
// rather than in the client because the SERVER is the side that has to spell it, and a constant is
// how a shared string stops being spelled twice.
object KompotCheckboxVariants {
    const val SWITCH = "switch"
}
