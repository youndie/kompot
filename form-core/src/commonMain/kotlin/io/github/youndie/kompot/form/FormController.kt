package io.github.youndie.kompot.form

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

// T is covariant (out) so that a FieldState of any concrete FieldValue can be held in a variable of
// type FieldState<FieldValue>.
public data class FieldState<out T : FieldValue>(
    val value: T?,
    val error: String? = null,
    val changed: Boolean = false,
)

public typealias PatchFetcher = suspend (fieldId: String, payload: Map<String, FieldValue>) -> FormPatch

@OptIn(ExperimentalCoroutinesApi::class)
public class FormController(
    private val schema: FormSchema,
    initialValues: Map<String, FieldValue> = emptyMap(),
    // Optional: how to fetch a patch from the backend for a field with triggersPatch = true. When it
    // is absent — the form needs no patching — requestPatchIfNeeded quietly does nothing.
    private val patchFetcher: PatchFetcher? = null,
    // Optional: the data source for fields with remote lookup (autocomplete). Without it
    // searchOptions returns an empty list.
    private val dataSourceResolver: RemoteDataSourceResolver? = null,
    // The scope in which the controller runs the background patch request itself: the UI layer no
    // longer launches or awaits a coroutine, it just reports the event. A real screen should pass its
    // own remembered scope so the request is cancelled together with the screen. The default is a
    // standalone scope, for places where that lifecycle binding does not matter — tests above all.
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    public val fieldsState: StateFlow<Map<String, FieldState<FieldValue>>>
        field =
        MutableStateFlow<Map<String, FieldState<FieldValue>>>(
            schema.fields.associate { fieldDef ->
                // The schema's own initial value, unless the caller passed one: a value handed to the
                // constructor is what a screen already had — a draft being resumed — and must win over
                // what the server suggested for an empty form.
                val initialVal = initialValues[fieldDef.fieldId] ?: fieldDef.initialValue
                fieldDef.fieldId to
                    FieldState(
                        value = initialVal,
                        error = null,
                        changed = initialVal != null, // pre-filled counts as changed
                    )
            },
        )

    // A counter of in-flight patch requests. It can exceed one when several fields trigger patches,
    // though mapLatest below allows at most one per trigger. isLoading is derived from it reactively,
    // with no manual isLoading.value = true/false from several places racing to overwrite each other.
    private val activePatchRequests = MutableStateFlow(0)

    public val isLoading: StateFlow<Boolean> =
        activePatchRequests
            .map { it > 0 }
            .stateIn(scope, SharingStarted.Eagerly, false)

    // The "field changed, a patch is needed" channel: the UI simply emits a fieldId and mapLatest
    // below decides when to cancel a previous unfinished request and send a new one.
    // replay = 1, not merely extraBufferCapacity, is mandatory. launchIn(scope) in init schedules the
    // collector's subscription but does not start it synchronously, so if requestPatchIfNeeded() is
    // called before the collect actually begins — a real race both in tests and in production on the
    // very first call after construction — extraBufferCapacity alone would not help: it buffers for an
    // ALREADY subscribed collector, not a future one, and the event would simply be lost. DROP_OLDEST
    // on top guarantees the single replay slot always holds the latest trigger.
    private val patchTriggerFlow =
        MutableSharedFlow<String>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    // A patch may ask for focus to move to a particular field (focusOn). FormController knows
    // nothing about focus itself; it just relays the request to the UI layer as an event.
    public val focusRequests: SharedFlow<String>
        field = MutableSharedFlow<String>(extraBufferCapacity = 1)

    init {
        // mapLatest is what resolves the race: if the trigger fires twice in a row — the user changed
        // the value again before the previous patch request answered — the earlier request is
        // cancelled automatically and its result can never overwrite the fresher input.
        patchTriggerFlow
            .mapLatest { fieldId -> performPatch(fieldId) }
            .launchIn(scope)
    }

    // A convenience for UI components: obtain a state of the required type without manual casts.
    // val state = formController.getTypedState<TextValue>("hisob_raqam")
    public inline fun <reified T : FieldValue> getTypedState(fieldId: String): FieldState<T> {
        val state = fieldsState.value[fieldId]

        // Cast safely to the requested type; if the field is missing or the type does not match,
        // return an empty state.
        @Suppress("UNCHECKED_CAST")
        return (state as? FieldState<T>) ?: FieldState(null)
    }

    public inline fun <reified T : FieldValue> getFieldFlow(fieldId: String): Flow<FieldState<T>> {
        return fieldsState
            .map { map ->
                @Suppress("UNCHECKED_CAST")
                (map[fieldId] as? FieldState<T>) ?: FieldState(null)
            }.distinctUntilChanged() // avoid recomposition when a different field changed
    }

    // Whether this particular field is visible right now (a snapshot). Fields without visibleIf are
    // always true.
    public fun isFieldVisible(fieldId: String): Boolean {
        val fieldDef = schema.fields.find { it.fieldId == fieldId } ?: return true
        return isVisible(fieldDef, fieldsState.value)
    }

    // The reactive visibility stream, recomputed when ANY field changes — a condition may reference
    // another field — but emitting only when the visibility result itself changes.
    public fun getVisibilityFlow(fieldId: String): Flow<Boolean> {
        val fieldDef = schema.fields.find { it.fieldId == fieldId } ?: return flowOf(true)
        return fieldsState.map { state -> isVisible(fieldDef, state) }.distinctUntilChanged()
    }

    private fun isVisible(
        fieldDef: FormFieldDefinition,
        state: Map<String, FieldState<FieldValue>>,
    ): Boolean {
        val condition = fieldDef.visibleIf ?: return true
        return condition.evaluate { fieldId -> state[fieldId]?.value }
    }

    // The value-update entry point, called from the UI.
    public fun onValueChanged(
        fieldId: String,
        newValue: FieldValue,
    ) {
        val fieldDef = schema.fields.find { it.fieldId == fieldId } ?: return

        fieldsState.update { currentMap ->
            val updated =
                currentMap + (
                    fieldId to
                        FieldState(
                            value = newValue,
                            // This field's own previous error is hidden as soon as the user starts
                            // editing it. It comes back on blur or on markAllAsChanged: showing an
                            // error on every keystroke would be noise.
                            error = null,
                            changed = true,
                        )
                )
            // OTHER fields that were already showing an error are revalidated immediately: their rule
            // may reference this field, and the outcome may have changed just now. Waiting until the
            // user touches that other field would show a stale error in the meantime.
            revalidateTouchedFields(updated, skip = fieldId)
        }
    }

    // Validation on blur. It checks this field only, not the whole form, and sets its error when a
    // rule fails. Called by the UI component when focus is lost. A field hidden by visibleIf is not
    // validated.
    public fun onFieldBlurred(fieldId: String) {
        val fieldDef = schema.fields.find { it.fieldId == fieldId } ?: return

        fieldsState.update { currentMap ->
            val current = currentMap[fieldId] ?: return@update currentMap
            val error =
                if (isVisible(fieldDef, currentMap)) {
                    validateField(current.value, fieldDef.rules) { id -> currentMap[id]?.value }
                } else {
                    null
                }

            currentMap + (fieldId to current.copy(error = error, changed = true))
        }
    }

    // Highlights a field with an error that came from the backend — a saga rejecting the operation
    // during its validation phase, say. Business validation of that kind is deliberately not
    // duplicated in a ValidationRule: it is the server's responsibility.
    public fun setFieldError(
        fieldId: String,
        errorMessage: String?,
    ) {
        fieldsState.update { currentMap ->
            val current = currentMap[fieldId] ?: FieldState(null)
            currentMap + (fieldId to current.copy(error = errorMessage, changed = true))
        }
    }

    // When the field has triggersPatch = true and the form has a patchFetcher, this reports the
    // "field changed" event and returns immediately — it does not suspend and is called synchronously
    // from the UI layer. The request itself runs in the controller's scope through mapLatest, so if
    // the field changes again before the server answers, the earlier request is cancelled
    // automatically. When patching is not configured, or the field does not need it, this quietly
    // does nothing.
    public fun requestPatchIfNeeded(fieldId: String) {
        val fieldDef = schema.fields.find { it.fieldId == fieldId } ?: return
        if (patchFetcher == null || !fieldDef.triggersPatch) return

        patchTriggerFlow.tryEmit(fieldId)
    }

    private suspend fun performPatch(fieldId: String) {
        activePatchRequests.update { it + 1 }
        try {
            // Send the backend a current snapshot of the whole form.
            val patch = patchFetcher!!.invoke(fieldId, getRawValues())
            applyPatch(patch)
        } catch (e: CancellationException) {
            // A normal cancellation — mapLatest dropped a stale request because a fresher one
            // arrived. It must not be turned into a field error, and must be rethrown.
            throw e
        } catch (e: Exception) {
            // A network failure — a 500, a timeout, no connectivity. The form must neither hang nor
            // take the application down; the error is shown under the field that triggered the patch.
            setFieldError(fieldId, "Failed to load data: ${e.message}")
        } finally {
            activePatchRequests.update { it - 1 }
        }
    }

    // Applies a patch received from the backend: updates values, clears the listed fields and, when
    // asked, requests a focus move. A patch never changes the form's set of fields — for that the
    // server must send a whole new form.
    public fun applyPatch(patch: FormPatch) {
        fieldsState.update { currentMap ->
            var updated = currentMap

            for ((fieldId, value) in patch.updates) {
                val current = updated[fieldId] ?: FieldState(null)
                updated = updated + (fieldId to current.copy(value = value, error = null, changed = true))
            }

            for (fieldId in patch.clearFields) {
                updated = updated + (fieldId to FieldState<FieldValue>(value = null, error = null, changed = false))
            }

            // A patch may have touched a field that a cross-field rule of some already-erroring
            // field depends on, so that error may no longer apply.
            revalidateTouchedFields(updated, skip = null)
        }

        patch.focusOn?.let { focusRequests.tryEmit(it) }
    }

    // Revalidates every field already touched (changed = true, so it may already be showing an
    // error), except `skip` — usually the field just edited, whose error was handled separately.
    // Untouched fields are left alone: their error is not displayed anyway, so computing it early
    // would serve nothing.
    private fun revalidateTouchedFields(
        map: Map<String, FieldState<FieldValue>>,
        skip: String?,
    ): Map<String, FieldState<FieldValue>> =
        map.mapValues { (otherId, state) ->
            if (otherId == skip || !state.changed) return@mapValues state
            val otherDef = schema.fields.find { it.fieldId == otherId } ?: return@mapValues state
            val error =
                if (isVisible(otherDef, map)) {
                    validateField(state.value, otherDef.rules) { id -> map[id]?.value }
                } else {
                    null
                }
            if (error == state.error) state else state.copy(error = error)
        }

    // Marks every field as changed, which submit needs in order to highlight empty required fields.
    // Fields hidden by visibleIf are not validated and must not block submission.
    public fun markAllAsChanged() {
        fieldsState.update { currentMap ->
            currentMap.mapValues { (fieldId, state) ->
                val fieldDef = schema.fields.find { it.fieldId == fieldId }
                val error =
                    if (fieldDef != null && isVisible(fieldDef, currentMap)) {
                        validateField(state.value, fieldDef.rules) { id -> currentMap[id]?.value }
                    } else {
                        null
                    }

                FieldState(
                    value = state.value,
                    error = error,
                    changed = true,
                )
            }
        }
    }

    private fun validateField(
        value: FieldValue?, // the value may be null
        rules: List<ValidationRule>,
        getFieldValue: (fieldId: String) -> FieldValue?,
    ): String? {
        for (rule in rules) {
            // A ValidationRule is expected to cope with null — RequiredRule, for one.
            if (!rule.validate(value, getFieldValue)) return rule.errorMessage
        }
        return null
    }

    // The form's raw values as they are, regardless of validity or visibility — what goes to the
    // patchFetcher, since the backend needs to see everything the user typed, including the
    // not-yet-valid.
    public fun getRawValues(): Map<String, FieldValue> =
        fieldsState.value.mapNotNull { (fieldId, state) -> state.value?.let { fieldId to it } }.toMap()

    // Remote lookup for autocomplete fields. With no dataSourceResolver it quietly returns an empty
    // list rather than throwing.
    public suspend fun searchOptions(
        dataSourceId: String,
        query: String,
    ): List<FieldValue> = dataSourceResolver?.search(dataSourceId, query) ?: emptyList()

    // Payload assembly. Returns Map<String, FieldValue> so that kotlinx.serialization can turn it
    // into JSON of the required shape directly.
    public fun getPayload(): Map<String, FieldValue>? {
        val currentState = fieldsState.value

        // First check whether any VISIBLE field has an error: a field hidden by visibleIf must not
        // block submission.
        val hasErrors =
            currentState.any { (fieldId, state) ->
                isFieldVisible(fieldId) && state.error != null
            }
        if (hasErrors) return null

        // When the form is valid, collect only visible fields that hold values: a hidden field's
        // value never reaches the server, even if something was once typed into it.
        return currentState
            .filterKeys { fieldId -> isFieldVisible(fieldId) }
            .filterValues { it.value != null }
            .mapValues { it.value.value!! }
    }
}
