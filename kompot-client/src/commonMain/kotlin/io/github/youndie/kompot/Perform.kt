package io.github.youndie.kompot

import io.github.youndie.kompot.commands.PerformAction
import io.github.youndie.kompot.form.FieldValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// The client half of PerformAction. It is the same shape as withLoginSubmit one level down: send,
// then FEED the action that comes back into the same handler, so that "the item was moved" hands over
// to "go to this screen" without a dispatch mechanism of its own.
//
// Two differences from withLoginSubmit, and both come from the action itself rather than from taste.
// There is no FormController to consult: what to send is already in the action, which is the whole
// point — a button on card N carries card N's payload. And it belongs in the GLOBAL chain rather than
// on one screen, because nothing about it is screen-specific.
//
// The transport stays outside: `perform` is a lambda the application supplies, the same way the
// toolkit takes no opinion on HTTP anywhere else. That is also where the Idempotency-Key required by
// SPEC.md §16.5 is attached — a key per attempt, not per screen.
public fun KompotActionHandler.withPerform(
    scope: CoroutineScope,
    perform: suspend (url: String, payload: Map<String, FieldValue>) -> KompotAction,
): KompotActionHandler =
    KompotActionHandler { action ->
        if (action is PerformAction) {
            scope.launch { handle(perform(action.url, action.payload)) }
        }
            // Forwarded even when it was handled, exactly as withLoginSubmit forwards a submit: an
            // analytics wrapper further along the chain has to see that the button was pressed.
        handle(action)
    }
