package io.github.youndie.kompot

import io.github.youndie.kompot.analytics.AnalyticsEvent
import io.github.youndie.kompot.analytics.AnalyticsTracker
import io.github.youndie.kompot.forms.SubmitFormAction
import io.github.youndie.kompot.form.FormController

    // A wrapper AROUND a handler rather than a handler itself. It adds no actual form submission —
    // only the analytics, in the one place where a screen's FormController and its action handler are
    // both in scope at once.
fun KompotActionHandler.withFormSubmitTracking(
    formController: FormController,
    tracker: AnalyticsTracker,
    formId: String,
): KompotActionHandler =
    KompotActionHandler { action ->
        if (action is SubmitFormAction) {
            tracker.track(AnalyticsEvent.FormSubmitAttempted(formId))
            formController.markAllAsChanged()
                // getPayload() != null means client-side validation passed, NOT a confirmation from
                // the server. This is an analytics signal, nothing more.
            val payload = formController.getPayload()
            tracker.track(
                if (payload != null) AnalyticsEvent.FormSubmitSucceeded(formId) else AnalyticsEvent.FormSubmitBlocked(formId),
            )
        }
        handle(action)
    }
