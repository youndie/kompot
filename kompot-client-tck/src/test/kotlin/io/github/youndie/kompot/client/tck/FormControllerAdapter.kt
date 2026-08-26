package io.github.youndie.kompot.client.tck

import io.github.youndie.kompot.form.FieldValue
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.form.FormPatch
import io.github.youndie.kompot.form.FormSchema
import io.github.youndie.kompot.form.standard.formStandardSerializersModule
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

// The first adapter, and the reason the format is not merely written down: a corpus nobody has run is
// a document. This one drives form-core the way a Compose screen does — the same calls in the same
// order — so what the corpus finds is a divergence of the client, not of a test harness.
class FormControllerAdapter : KompotFormClient {
    private lateinit var controller: FormController
    private val sent = mutableListOf<JsonObject>()

    override fun load(form: JsonObject) {
        sent.clear()
        controller =
            FormController(
                schema = json.decodeFromJsonElement(FormSchema.serializer(), form),
                // Recording what the client would send, and answering the way a server that changes
                // nothing does. An empty patch keeps the case about the REQUEST: a fetcher that also
                // returned updates would mix the sending rule with the applying one, and §9.6 already
                // has cases for the second.
                patchFetcher = { fieldId, values ->
                    sent +=
                        buildJsonObject {
                            put("kind", JsonPrimitive("patch"))
                            put("fieldId", JsonPrimitive(fieldId))
                            put("values", JsonObject(values.mapValues { (_, value) -> json.encodeToJsonElement(fieldValue, value) }))
                        }
                    FormPatch()
                },
                // The patch runs in the controller's own scope through mapLatest; the corpus is
                // synchronous, so it runs here on a scheduler the case can drain.
                scope = CoroutineScope(patchDispatcher),
            )
    }

    override fun set(
        fieldId: String,
        value: JsonObject,
    ) {
        controller.onValueChanged(fieldId, json.decodeFromJsonElement(fieldValue, value))
        controller.requestPatchIfNeeded(fieldId)
    }

    override fun blur(fieldId: String) = controller.onFieldBlurred(fieldId)

    override fun requests(): List<JsonObject> {
        // Everything the controller queued has to have run before the answer is read, or a case would
        // see the state of a request that is still on its way — which is exactly the shape of bug the
        // patch rule is about.
        patchDispatcher.scheduler.advanceUntilIdle()
        return sent.toList()
    }

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
        // One dispatcher for the whole adapter: a case sets a value, the controller queues the patch,
        // and requests() drains it. A real screen has a remembered scope instead.
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        val patchDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher()

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
