package io.github.youndie.kompot.studio

import io.github.youndie.kompot.form.FieldValue
import io.github.youndie.kompot.form.standard.AmountValue
import io.github.youndie.kompot.form.standard.BooleanValue
import io.github.youndie.kompot.form.standard.EntityValue
import io.github.youndie.kompot.form.standard.TextValue
import io.github.youndie.kompot.preview.KompotPreviewState
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// A FORM IS NOT ONE PICTURE. Empty, filled, and showing every validation error are three screens, and
// the difference between them is the point of looking: a required-field message that never fits, a
// helper text that moves the button off the fold, a field that only misbehaves once it has a value.
internal enum class FormState {
    EMPTY,
    FILLED,
    ERRORS,
}

internal fun previewState(
    state: FormState,
    body: JsonElement?,
): KompotPreviewState =
    when (state) {
        FormState.EMPTY -> KompotPreviewState()

        // Nothing typed and everything touched: errors are shown on a field the person has LEFT, so an
        // untouched form is valid-looking however empty it is. This is the "somebody pressed submit on
        // a blank form" picture, without a fake submit.
        FormState.ERRORS -> KompotPreviewState(allFieldsChanged = true)

        FormState.FILLED -> KompotPreviewState(values = sampleValues(body))
    }

// Read off the WIRE schema rather than off decoded field definitions, and that is what keeps this
// working for a deployment's own field type: an unfamiliar type gets text, which is what an unfamiliar
// field most likely is, instead of an exception or an empty form pretending to be filled.
private fun sampleValues(body: JsonElement?): Map<String, FieldValue> {
    val fields =
        ((body as? JsonObject)?.get("schema") as? JsonObject)?.get("fields") as? JsonArray
            ?: return emptyMap()

    return fields.mapNotNull { field ->
        val definition = field as? JsonObject ?: return@mapNotNull null
        val fieldId = (definition["fieldId"] as? JsonPrimitive)?.content ?: return@mapNotNull null
        fieldId to sampleFor((definition["type"] as? JsonPrimitive)?.content)
    }.toMap()
}

private fun sampleFor(wireType: String?): FieldValue =
    when (wireType) {
        "amount_field" -> AmountValue(long = SAMPLE_AMOUNT)
        "checkbox_field" -> BooleanValue(value = true)
        "selection_field", "autocomplete_field" -> EntityValue(id = "sample", title = "Sample")
        else -> TextValue(text = "Sample")
    }

// A number with two digits after the point once a currency is put on it, and small enough that no
// balance rule refuses it for the wrong reason.
private const val SAMPLE_AMOUNT = 1_000L
