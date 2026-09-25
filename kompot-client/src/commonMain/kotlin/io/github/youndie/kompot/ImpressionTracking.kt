package io.github.youndie.kompot

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import io.github.youndie.kompot.analytics.AnalyticsEvent
import io.github.youndie.kompot.analytics.AnalyticsTracker
import io.github.youndie.kompot.analytics.KompotEventNamingRegistry
import io.github.youndie.kompot.form.FormController
import kotlinx.coroutines.delay
import kotlin.reflect.KClass

/**
 * When a node counts as SHOWN. An impression used to be "entered the composition", which in a plain
 * column is the moment the screen opens — so a card at the bottom of a long screen was counted for
 * everybody, scrolled to or not, and an A/B comparison of blocks below the first screen was inflated
 * for all of them (B-54).
 *
 * @property minVisibleFraction the share of the node's area that has to be inside the window. Along an
 *   axis where the node is longer than the window, the window's length is the whole.
 * @property minVisibleMillis how long it has to stay there; `0` counts the first frame it is.
 * @property track which nodes are counted at all. Every node was — a row of layout included — and the
 *   unit an experiment measures is usually a banner or a card, not the column that holds it. The
 *   server names those nodes by the form of their `id`, agreed with the application (SPEC.md §4.3):
 *
 * ```kotlin
 * renderers.withImpressionTracking(tracker, naming, ImpressionVisibility(track = { it.id.startsWith("promo-") }))
 * ```
 */
public data class ImpressionVisibility(
    val minVisibleFraction: Float = 0.5f,
    val minVisibleMillis: Long = 0L,
    val track: (KompotComponent) -> Boolean = { true },
)

// Wraps EVERY renderer in an already merged map rather than touching the registry itself.
// RenderNode is the single dispatch point every render passes through — the root, the children of
// a column, a row or a table, the items of a lazy list — so substituting the values of the map
// BEFORE it reaches the registry covers 100% of nodes without a line changing in the renderers.
public fun Map<KClass<out KompotComponent>, KompotComponentRenderer<out KompotComponent>>.withImpressionTracking(
    tracker: AnalyticsTracker,
    naming: KompotEventNamingRegistry,
): Map<KClass<out KompotComponent>, KompotComponentRenderer<out KompotComponent>> = withImpressionTracking(tracker, naming, ImpressionVisibility())

public fun Map<KClass<out KompotComponent>, KompotComponentRenderer<out KompotComponent>>.withImpressionTracking(
    tracker: AnalyticsTracker,
    naming: KompotEventNamingRegistry,
    visibility: ImpressionVisibility,
): Map<KClass<out KompotComponent>, KompotComponentRenderer<out KompotComponent>> =
    mapValues { (_, renderer) ->
        @Suppress("UNCHECKED_CAST") // the same unchecked cast the registry's dispatch does
        ImpressionTrackingRenderer(renderer as KompotComponentRenderer<KompotComponent>, tracker, naming, visibility)
    }

private class ImpressionTrackingRenderer<T : KompotComponent>(
    private val delegate: KompotComponentRenderer<T>,
    private val tracker: AnalyticsTracker,
    private val naming: KompotEventNamingRegistry,
    private val visibility: ImpressionVisibility,
) : KompotComponentRenderer<T> {
    @Composable
    override fun Render(
        component: T,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        if (!visibility.track(component)) {
            delegate.Render(component, actionHandler, formController)
            return
        }

        // Keyed by id: once per stay in the composition, as before — leaving the screen and coming
        // back, or an item scrolled out of a lazy list and back in, is genuinely shown again.
        var visible by remember(component.id) { mutableStateOf(false) }
        var counted by remember(component.id) { mutableStateOf(false) }

        LaunchedEffect(component.id, visible) {
            if (!visible || counted) return@LaunchedEffect
            // Cancelled by leaving the window before the time is up, since `visible` keys it.
            if (visibility.minVisibleMillis > 0) delay(visibility.minVisibleMillis)
            counted = true
            tracker.track(AnalyticsEvent.ComponentImpression(naming.describe(component)))
        }

        // A box that passes its constraints through untouched, only to learn where the node is: the
        // renderer's own root is not reachable from here. boundsInWindow is already clipped by every
        // scrolling or clipping ancestor, so its area is the part a person can see.
        Box(
            propagateMinConstraints = true,
            modifier =
                Modifier.onGloballyPositioned { coordinates ->
                    // Per axis, against the smaller of the node and the window: a column three screens
                    // tall is never half inside the window, yet filling its height is as seen as it gets.
                    val window = coordinates.findRootCoordinates().size
                    val seen = coordinates.boundsInWindow()
                    val across = seen.width / minOf(coordinates.size.width, window.width)
                    val along = seen.height / minOf(coordinates.size.height, window.height)
                    visible = coordinates.size.width > 0 && coordinates.size.height > 0 &&
                        across * along >= visibility.minVisibleFraction
                },
        ) {
            delegate.Render(component, actionHandler, formController)
        }
    }
}
