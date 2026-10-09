package io.github.youndie.kompot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.youndie.kompot.realtime.KompotRealtimeSource
import kotlinx.coroutines.CancellationException

/**
 * A map of nodes by id that `RenderNode` substitutes, read after [LocalKompotNodeOverrides].
 *
 * Kept so that a map provided here still draws, and deprecated because such a map lives outside the
 * screen: nothing drops its entries when a new tree arrives or when a node around them is replaced,
 * which is exactly how a frame came to cover the node a refresh brought (SPEC.md §4.4).
 * [KompotRealtimeProvider] no longer provides it.
 */
@Deprecated(
    "A map provided here outlives the tree it was written against. Write through KompotNodeOverrides " +
        "(LocalKompotNodeOverrides), which drops an override when a new tree arrives.",
)
public val LocalKompotRealtimeUpdates: ProvidableCompositionLocal<Map<String, KompotComponent>> =
    staticCompositionLocalOf { emptyMap() }

// Frames go into the screen's override store, the same one the screen drops when a new tree arrives:
// a store of the provider's own, kept under remember(topic), outlived every refresh on the same topic
// and went on covering the nodes the refresh brought.
//
// The store is the one found above, or one of the provider's own when there is none — in which case
// leaving the tree or changing the topic drops it along with the overrides it held. LaunchedEffect
// cancels the previous subscription itself, closing the connection; no manager with a manual clear()
// or scope.cancel() is needed.
@Composable
public fun KompotRealtimeProvider(
    topic: String,
    source: KompotRealtimeSource,
    content: @Composable () -> Unit,
    onUpdate: (suspend () -> Unit)? = null,
) {
    val overrides = LocalKompotNodeOverrides.current ?: remember(topic) { KompotNodeOverrides() }
    val sink = LocalKompotDegradationSink.current

    LaunchedEffect(topic, onUpdate, overrides) {
        try {
            source.subscribe(topic).collect { message ->
                overrides.override(message.componentId, message.component)
                onUpdate?.invoke()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // The screen survives — it keeps the last tree and the updates it had — which is the
            // degradation the toolkit promises. What it may not do is survive silently.
            sink.onRealtimeFailure(topic, e)
        }
    }

    CompositionLocalProvider(LocalKompotNodeOverrides provides overrides) {
        content()
    }
}
