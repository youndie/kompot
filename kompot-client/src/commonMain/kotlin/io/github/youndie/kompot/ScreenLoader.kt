package io.github.youndie.kompot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.coroutines.cancellation.CancellationException

/**
 * Where a [KompotScreenLoader] is: the tree it draws, whether a load is on its way, and why the last
 * one failed. Hoisted, so the application can draw its own indicator over a kept tree and tell an
 * error over a tree (a notice) from an error with nothing under it (a screen).
 */
@Stable
public class KompotScreenLoaderState {
    /** The tree on screen; `null` until the first one of this screen arrives. */
    public var screen: KompotComponent? by mutableStateOf(null)
        internal set

    /** A load is on its way — the first one, or the next key while [screen] stays drawn. */
    public var isLoading: Boolean by mutableStateOf(false)
        internal set

    /** Why the last load failed; `null` once a new one starts. */
    public var failure: Throwable? by mutableStateOf(null)
        internal set

    // Which screen the tree belongs to, and which request the failure answered: the loader draws them
    // only while they still are the current ones, so the frame between a change and its effect does
    // not draw the previous screen's tree or the previous request's error.
    internal var screenOf: Any? by mutableStateOf(NothingLoaded)
    internal var failureOf: Any? by mutableStateOf(NothingLoaded)

    // How many loads have completed: each one is a delivery, so the screen drawn under the loader drops
    // its overrides even when the tree that came is equal to the one it draws (SPEC.md §4.4).
    internal var arrivals: Long by mutableLongStateOf(0L)

    internal object NothingLoaded
}

@Composable
public fun rememberKompotScreenLoaderState(): KompotScreenLoaderState = remember { KompotScreenLoaderState() }

private data class Request(
    val key: Any?,
    val screenKey: Any?,
)

// Slots rather than defaults the toolkit draws: loading and error are the application's screens
// (SPEC.md §12.6), and a default of ours would be a spinner in the wrong place and an English word on
// every phone. `loading` may be left empty — nothing is drawn — while `failed` has to be given: a
// screen that failed and showed nothing looks exactly like one that is still loading.

/**
 * Loads a screen with [load] and draws it with [content]; while there is nothing to draw yet,
 * [loading], and when a load did not arrive, [failed] with a retry.
 *
 * The source is the application's — a request, `CachedKompotScreenProvider.getScreen`, a fixture — so
 * every screen goes through the same frames whatever it comes from.
 *
 * **A new [key] of the same screen keeps the drawn tree while it loads** (SPEC.md §12.6). Which keys
 * are one screen only the application knows, and it says so with [screenKey]: a new [key] under the
 * same [screenKey] loads behind the tree that is drawn, and [loading] is drawn only when there is
 * nothing to draw — on the first load, and when [screenKey] changes. An application puts the whole
 * address in the key, filters and query included; passing the path as [screenKey] is what stops every
 * filter from looking like a jump to another screen, with the tree, the scroll and every opened node
 * coming down with it. Left at its default — [key] itself — every new key is another screen, as before:
 * a loader that serves different screens must not keep a stale one on screen, tappable, while the next
 * one loads.
 *
 * A failure keeps the drawn tree of the same screen too: [failed] gets the cause and a retry and is
 * drawn after [content], in the same parent — whether that is a notice over the tree or a screen is the
 * application's call, and [KompotScreenLoaderState.screen] tells it which case it is in. With no tree,
 * [failed] is drawn alone. [state] says whether a load is in flight, for the application's own
 * indicator.
 *
 * [failed]'s retry loads the same key again. Cancellation is not a failure: leaving the screen while
 * it loads draws nothing.
 *
 * **Every load that completes is an arrival** (SPEC.md §4.4): the [KompotScreen] or [KompotLazyScreen]
 * that [content] draws drops the overrides written before it — a live frame, an `update` — even when
 * the tree that came is equal to the one drawn. That is "back" after an `update`, and a `refresh` the
 * application runs as a new [key] (a counter in it) under the same [screenKey]. A load still on its
 * way drops nothing, and state under ids stays: only the overrides go.
 */
@Composable
public fun KompotScreenLoader(
    key: Any?,
    load: suspend () -> KompotComponent,
    failed: @Composable (cause: Throwable, retry: () -> Unit) -> Unit,
    loading: @Composable () -> Unit = {},
    screenKey: Any? = key,
    state: KompotScreenLoaderState = rememberKompotScreenLoaderState(),
    content: @Composable (screen: KompotComponent) -> Unit,
) {
    var attempt by remember(key) { mutableIntStateOf(0) }
    val request = Request(key, screenKey)

    LaunchedEffect(key, screenKey, attempt, state) {
        // Another screen: what is drawn is not this one, so there is nothing to keep.
        if (state.screenOf != screenKey) state.screen = null
        state.failure = null
        state.isLoading = true
        try {
            val next = load()
            state.screen = next
            state.screenOf = screenKey
            state.arrivals++
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            state.failure = error
            state.failureOf = request
        }
        state.isLoading = false
    }
    // A cancelled load never reaches the line above, and a hoisted state must not go on saying
    // "loading" for a loader that has left.
    DisposableEffect(state) { onDispose { state.isLoading = false } }

    val tree = state.screen?.takeIf { state.screenOf == screenKey }
    val failure = state.failure?.takeIf { state.failureOf == request }

    // The content's place does not depend on the failure: a failure arriving over the tree must not
    // take the tree's state down with it.
    if (tree != null) {
        // The load's number goes down with its tree: the screen below takes it as the tree's arrival.
        CompositionLocalProvider(LocalKompotTreeArrival provides state.arrivals) { content(tree) }
    } else if (failure == null) {
        loading()
    }
    if (failure != null) failed(failure) { attempt++ }
}

// The signature before screenKey and state, kept for code compiled against it.
@Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
@Composable
public fun KompotScreenLoader(
    key: Any?,
    load: suspend () -> KompotComponent,
    failed: @Composable (cause: Throwable, retry: () -> Unit) -> Unit,
    loading: @Composable () -> Unit = {},
    content: @Composable (screen: KompotComponent) -> Unit,
) {
    KompotScreenLoader(key = key, load = load, failed = failed, loading = loading, content = content)
}
