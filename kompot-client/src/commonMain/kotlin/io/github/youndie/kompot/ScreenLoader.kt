package io.github.youndie.kompot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.coroutines.cancellation.CancellationException

// Where a screen is on its way to being drawn. The two states without a tree are the application's
// to draw (SPEC.md §12.6).
private sealed interface KompotScreenState {
    data object Loading : KompotScreenState

    data class Loaded(
        val screen: KompotComponent,
    ) : KompotScreenState

    data class Failed(
        val cause: Throwable,
    ) : KompotScreenState
}

/**
 * Loads a screen with [load] and draws it with [content]; while it is on its way, [loading], and when
 * it did not arrive, [failed] with a retry.
 *
 * The source is the application's — a request, `CachedKompotScreenProvider.getScreen`, a fixture — so
 * every screen goes through the same two frames whatever it comes from. A new [key] loads again;
 * [failed]'s retry loads the same key again. Cancellation is not a failure: leaving the screen while it
 * loads draws nothing.
 */
// Slots rather than defaults the toolkit draws: loading and error are the application's screens
// (SPEC.md §12.6), and a default of ours would be a spinner in the wrong place and an English word on
// every phone. `loading` may be left empty — nothing is drawn — while `failed` has to be given: a
// screen that failed and showed nothing looks exactly like one that is still loading.
@Composable
public fun KompotScreenLoader(
    key: Any?,
    load: suspend () -> KompotComponent,
    failed: @Composable (cause: Throwable, retry: () -> Unit) -> Unit,
    loading: @Composable () -> Unit = {},
    content: @Composable (screen: KompotComponent) -> Unit,
) {
    var attempt by remember(key) { mutableIntStateOf(0) }
    var state by remember(key) { mutableStateOf<KompotScreenState>(KompotScreenState.Loading) }

    LaunchedEffect(key, attempt) {
        state = KompotScreenState.Loading
        state =
            try {
                KompotScreenState.Loaded(load())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                KompotScreenState.Failed(error)
            }
    }

    when (val current = state) {
        KompotScreenState.Loading -> loading()
        is KompotScreenState.Loaded -> content(current.screen)
        is KompotScreenState.Failed -> failed(current.cause) { attempt++ }
    }
}
