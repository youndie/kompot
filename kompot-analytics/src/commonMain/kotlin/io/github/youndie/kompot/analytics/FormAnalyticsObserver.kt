package io.github.youndie.kompot.analytics

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import io.github.youndie.kompot.form.FieldState
import io.github.youndie.kompot.form.FieldValue
import io.github.youndie.kompot.form.FormController

// FormController offers no callbacks on field changes, so this subscribes to fieldsState from the
// outside and diffs consecutive snapshots itself, changing nothing in form-core. It returns a Job:
// when to cancel it is the caller's decision.
fun observeFormAnalytics(
    formController: FormController,
    tracker: AnalyticsTracker,
    scope: CoroutineScope,
    formId: String,
): Job {
    // Seeded with the current snapshot rather than an empty map: fieldsState is a StateFlow, so the
    // first emission arrives with the state as it already is — prefilled values included — and without
    // the seed that would be miscounted as "the user changed a field".
    var previous: Map<String, FieldState<FieldValue>> = formController.fieldsState.value

    return formController.fieldsState
        .onEach { current ->
            for ((fieldId, state) in current) {
                val prevState = previous[fieldId]

                if (prevState?.value != state.value) {
                    tracker.track(AnalyticsEvent.FieldChanged(formId, fieldId))
                }

                val error = state.error
                if (error != null && prevState?.error != error) {
                    tracker.track(AnalyticsEvent.FieldValidationErrorShown(formId, fieldId, error))
                }
            }
            previous = current
        }.launchIn(scope)
}
