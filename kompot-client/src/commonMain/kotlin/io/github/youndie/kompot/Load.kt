package io.github.youndie.kompot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.youndie.kompot.commands.LoadAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The `load`s of one screen: whether one is on its way, for the application's own indicator, and which
 * press is the last — the only one whose answer is run (SPEC.md §16.4). One per screen: two screens
 * sharing it would drop each other's answers.
 */
@Stable
public class KompotLoadState {
    /** A `load` was pressed on this screen and its answer has not arrived yet. */
    public var isLoading: Boolean by mutableStateOf(false)
        internal set

    // Not state: nothing draws them. Written and read on the thread the handler runs on, as every
    // action is.
    internal var lastPress: Long = 0L
    internal var inFlight: Job? = null
}

@Composable
public fun rememberKompotLoadState(): KompotLoadState = remember { KompotLoadState() }

/**
 * Runs `load` (SPEC.md §16.4): [load] GETs the url — an endpoint of kind `load`, with no
 * Idempotency-Key, since nothing changes — and its answer is handed down the chain like `withPerform`'s,
 * so an `update` reaches `withUpdates` and a `navigate` the application. What this client cannot
 * understand in the answer is reported to [degradationSink].
 *
 * **The last press wins.** A `load` pressed while another is on its way cancels it, and an answer that
 * still arrives for an earlier press is dropped: filters are pressed one after another, the network
 * does not keep their order, and without this the screen would end on the person's previous choice.
 * "The same screen" is [state]: one per screen, remembered with the screen; it also says whether a
 * load is in flight. Left at its default, this handler is the screen.
 *
 * A failure of [load] is the application's, as it is for `withPerform`: catch it there and answer with
 * a `show_message`, or let it reach the scope. [KompotLoadState.isLoading] goes back to false either way.
 * The action is forwarded for an analytics wrapper further along the chain.
 */
public fun KompotActionHandler.withLoad(
    scope: CoroutineScope,
    state: KompotLoadState = KompotLoadState(),
    degradationSink: KompotDegradationSink = KompotPrintingDegradationSink,
    load: suspend (url: String) -> KompotAction,
): KompotActionHandler =
    KompotActionHandler { action ->
        if (action is LoadAction) {
            val press = ++state.lastPress
            state.inFlight?.cancel()
            state.isLoading = true
            state.inFlight =
                scope.launch {
                    try {
                        val answer = load(action.url)
                        // A load that ignores cancellation still returns: the press number is what
                        // decides, not whether the coroutine noticed it was cancelled.
                        if (press == state.lastPress) {
                            state.isLoading = false
                            degradationSink.reportUnknown(answer)
                            handle(answer)
                        }
                    } finally {
                        if (press == state.lastPress) state.isLoading = false
                    }
                }
        }
        handle(action)
    }
