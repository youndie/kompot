package io.github.youndie.kompot

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import io.github.youndie.kompot.forms.SubmitFormAction
import io.github.youndie.kompot.form.FieldValue
import io.github.youndie.kompot.form.FormController

    // A handler for a form submit on the client. It sends the payload to the server and FEEDS the
    // action that comes back into the same handler — reusing the "substitute the action for the rest
    // of the chain" idea one level up. That is how "the form was submitted" hands over to "the session
    // was updated" without a dispatch mechanism of its own.
    //
    // Like the analytics wrapper, this wraps a handler rather than being one, and is applied on a
    // login screen rather than in the global chain: it needs a concrete FormController, which does not
    // exist yet when the global chain is assembled.
fun KompotActionHandler.withLoginSubmit(
    scope: CoroutineScope,
    formController: FormController,
    formId: String,
    submit: suspend (payload: Map<String, FieldValue>) -> KompotAction,
): KompotActionHandler =
    KompotActionHandler { action ->
        if (action is SubmitFormAction && action.formId == formId) {
                // markAllAsChanged() forces validation of EVERY field, untouched ones included:
                // otherwise getPayload() on an empty required field that never lost focus returns an
                // empty map rather than null, and would not block the submit.
            formController.markAllAsChanged()
            formController.getPayload()?.let { payload ->
                scope.launch { handle(submit(payload)) }
            }
        }
        handle(action)
    }
