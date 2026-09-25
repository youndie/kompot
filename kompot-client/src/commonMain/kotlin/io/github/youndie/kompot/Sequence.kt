package io.github.youndie.kompot

import io.github.youndie.kompot.standard.SequenceAction

/**
 * Runs `sequence` (SPEC.md §16.4): each action in order, through [followUp].
 *
 * [followUp] must be the TOP of the chain, as for `withSnackbarMessages`: a sequence is usually an
 * answer — to a submit, to a perform — and its parts are ordinary actions, a `perform` among them.
 * Sent to the handler this wraps, they would skip everything wrapped around it:
 *
 * ```
 * lateinit var handler: KompotActionHandler
 * handler = appHandler.withSequences { handler.handle(it) }.withPerform(scope, perform)
 * ```
 *
 * In order means dispatched in order, not completed in order: a `perform` is sent and the next part
 * goes at once, without waiting for its answer. And no part can fail the rest — a handler has no notion
 * of failure to stop on — so a sequence is not a transaction. The sequence itself is forwarded too, as
 * `withPerform` forwards, for an analytics wrapper further along the chain.
 */
public fun KompotActionHandler.withSequences(followUp: (KompotAction) -> Unit = { handle(it) }): KompotActionHandler =
    KompotActionHandler { action ->
        if (action is SequenceAction) action.actions.forEach(followUp)
        handle(action)
    }
