package io.github.youndie.kompot

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import io.github.youndie.kompot.wizard.FinishWizardAction
import io.github.youndie.kompot.wizard.NextStepAction
import io.github.youndie.kompot.wizard.PrevStepAction
import io.github.youndie.kompot.form.FieldValue
import io.github.youndie.kompot.form.FormController

// The counterpart of withLoginSubmit for wizard navigation: it intercepts Next/Finish/Prev rather
// than a submit action, because a wizard has THREE transitions instead of one, and Back must not
// require a valid form — validation runs for Next and Finish only, on the same principle as a patch
// request, which does not wait for validity either.
//
// Unlike withLoginSubmit, the result of onNext/onFinish/onBack is NOT fed back into handle()
// automatically. The answer to a resume is either a step result — the next step of the same screen,
// which must replace that screen's own local state rather than travel the shared interceptor chain —
// or any other action, a "the flow is over" navigation for instance, which DOES belong in the shared
// chain. Only the calling screen can tell those apart, so the callbacks return Unit.
public fun KompotActionHandler.withWizardNavigation(
    scope: CoroutineScope,
    formController: FormController,
    formId: String,
    onNext: suspend (payload: Map<String, FieldValue>) -> Unit,
    onFinish: suspend (payload: Map<String, FieldValue>) -> Unit,
    onBack: suspend (raw: Map<String, FieldValue>) -> Unit,
): KompotActionHandler =
    KompotActionHandler { action ->
        when {
            action is NextStepAction && action.formId == formId -> {
                formController.markAllAsChanged()
                formController.getPayload()?.let { payload -> scope.launch { onNext(payload) } }
            }

            action is FinishWizardAction && action.formId == formId -> {
                formController.markAllAsChanged()
                formController.getPayload()?.let { payload -> scope.launch { onFinish(payload) } }
            }

            action is PrevStepAction && action.formId == formId -> {
                scope.launch { onBack(formController.getRawValues()) }
            }

            else -> handle(action)
        }
    }
