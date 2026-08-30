package io.github.youndie.kompot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CancellationException
import io.github.youndie.kompot.realtime.KompotRealtimeSource

    // Live component updates by id, delivered over the update channel. RenderNode substitutes a value
    // from this map for the original component before dispatching — the single integration point, and
    // no concrete renderer knows this mechanism exists. The default is an empty immutable map, so a
    // screen without a provider behaves exactly as before.
public val LocalKompotRealtimeUpdates: ProvidableCompositionLocal<Map<String, KompotComponent>> =
    staticCompositionLocalOf { emptyMap() }

    // The subscription lives exactly as long as this node's composition: the map is created through
    // remember(topic), so leaving the tree — or changing the topic — drops it along with the updates
    // it held, and LaunchedEffect(topic) cancels the previous subscription itself, closing the
    // connection. No separate manager with a manual clear() or scope.cancel() is needed.
@Composable
public fun KompotRealtimeProvider(
    topic: String,
    source: KompotRealtimeSource,
    content: @Composable () -> Unit,
    onUpdate: (suspend () -> Unit)? = null,
) {
    val updates = remember(topic) { mutableStateMapOf<String, KompotComponent>() }

    LaunchedEffect(topic, onUpdate) {
        try {
            source.subscribe(topic).collect { message ->
                updates[message.componentId] = message.component
                onUpdate?.invoke()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // TODO: Log the exception
        }
    }

    CompositionLocalProvider(LocalKompotRealtimeUpdates provides updates) {
        content()
    }
}
