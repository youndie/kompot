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
    // A unit suffix for visual formatting, for example "USD"
    val currencySuffix: String? = null,
    // When set, the renderer watches this field's EntityValue locally, with no server round trip,
    // and as soon as rawMetadata["currency"] appears there it carries the unit over to the amount.
    // Picking a source instantly changes the amount's unit, because the data is already on the
    // client.
    val currencyFromField: String? = null,
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

// A non-editable "label plus value" field. It is not bound to the FormController: the value arrives
// wholly from the server at render time, like ordinary text but styled as an input.
@Serializable
@SerialName("read_only_field")
@KompotComponentMarker
data class ReadOnlyFieldComponent(
    override val id: String,
    override val modifiers: List<KompotModifierNode> = emptyList(),
    val label: String,
    val value: String,
    val helperText: String? = null,
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
