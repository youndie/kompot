package io.github.youndie.kompot.ds.material

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotActionHandler
import io.github.youndie.kompot.standard.MessageLevel
import io.github.youndie.kompot.standard.ShowMessageAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Shows `show_message` (SPEC.md §16.4) as a Material 3 snackbar in [host], and hands the message's own
 * button to this handler when it is pressed.
 *
 * The protocol leaves the look of a message to the client; this is the Material answer, for an app
 * whose screen already has a `SnackbarHost`. Put it in the same chain as `withPerform`, inside it, so
 * the action a `perform` answers with reaches it — and give [followUp] the TOP of the chain, so the
 * action on the message's own button goes through all of it:
 *
 * ```
 * lateinit var handler: KompotActionHandler
 * handler = appHandler.withSnackbarMessages(snackbarHostState, scope) { handler.handle(it) }.withPerform(scope, perform)
 * ```
 *
 * [followUp] is the whole point of the second line. The button's action is a new action like any
 * other — "Undo" is typically a `perform` — and sent to the handler this wraps it would skip
 * everything wrapped around it, `withPerform` included: the undo would never be sent. The default is
 * only right for a chain with nothing outside this wrapper.
 */
public fun KompotActionHandler.withSnackbarMessages(
    host: SnackbarHostState,
    scope: CoroutineScope,
    followUp: (KompotAction) -> Unit = { handle(it) },
): KompotActionHandler =
    KompotActionHandler { action ->
        if (action is ShowMessageAction) {
            // The button needs both halves: words without a destination would be a button that does
            // nothing, a destination without words a button nobody can read.
            val follow = action.action?.takeIf { action.actionLabel != null }
            scope.launch {
                val result =
                    host.showSnackbar(
                        message = action.text,
                        actionLabel = if (follow != null) action.actionLabel else null,
                        // Material has no severity on a snackbar; what it can say is how long an error
                        // stays, and an error is what a person most needs time to read.
                        duration =
                            when {
                                follow != null -> SnackbarDuration.Long
                                action.level == MessageLevel.ERROR -> SnackbarDuration.Long
                                else -> SnackbarDuration.Short
                            },
                    )
                if (result == SnackbarResult.ActionPerformed && follow != null) followUp(follow)
            }
        }
        // Forwarded even when shown, as withPerform forwards: an analytics wrapper further along the
        // chain has to see that the message was raised.
        handle(action)
    }
