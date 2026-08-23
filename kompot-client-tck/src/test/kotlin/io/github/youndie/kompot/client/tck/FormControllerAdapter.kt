package io.github.youndie.kompot.client.tck

import io.github.youndie.kompot.form.FieldValue
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.form.FormPatch
import io.github.youndie.kompot.form.FormSchema
import io.github.youndie.kompot.form.standard.formStandardSerializersModule
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

// The first adapter, and the reason the format is not merely written down: a corpus nobody has run is
// a document. This one drives form-core the way a Compose screen does — the same calls in the same
// order — so what the corpus finds is a divergence of the client, not of a test harness.
class FormControllerAdapter : KompotFormClient {
    private lateinit var controller: FormController

    override fun load(form: JsonObject) {
        controller = FormController(json.decodeFromJsonElement(FormSchema.serializer(), form))
    }

    override fun set(
        fieldId: String,
        value: JsonObject,
    ) {
        controller.onValueChanged(fieldId, json.decodeFromJsonElement(fieldValue, value))
    }

    override fun blur(fieldId: String) = controller.onFieldBlurred(fieldId)

    override fun applyPatch(patch: JsonObject) = controller.applyPatch(json.decodeFromJsonElement(FormPatch.serializer(), patch))

    // What a submit does before it sends: force every field to be validated, untouched ones included.
    override fun submit() = controller.markAllAsChanged()

    override fun visibleFields(): List<String> = controller.fieldsState.value.keys.filter { controller.isFieldVisible(it) }

    override fun errors(): Map<String, String> =
        controller.fieldsState.value.mapNotNull { (fieldId, state) -> state.error?.let { fieldId to it } }.toMap()

    override fun payload(): JsonObject? =
        controller.getPayload()?.let { payload ->
            JsonObject(payload.mapValues { (_, value) -> json.encodeToJsonElement(fieldValue, value) })
        }

    private companion object {
        val json =
            Json {
                classDiscriminator = "type"
                ignoreUnknownKeys = true
                serializersModule = formStandardSerializersModule
            }

        // FieldValue is a plain interface with no generated serializer, so it cannot be inferred —
        // exactly the rule §4.5 states for a screen's root. On the JVM the reified form would have
        // resolved this by reflection and hidden the requirement; here it does not compile at all,
        // which is the better of the two.
        val fieldValue = PolymorphicSerializer(FieldValue::class)
    }
}
