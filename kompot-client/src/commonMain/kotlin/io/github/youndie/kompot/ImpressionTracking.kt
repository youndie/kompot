package io.github.youndie.kompot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import io.github.youndie.kompot.analytics.AnalyticsEvent
import io.github.youndie.kompot.analytics.AnalyticsTracker
import io.github.youndie.kompot.analytics.KompotEventNamingRegistry
import io.github.youndie.kompot.form.FormController
import kotlin.reflect.KClass

    // Wraps EVERY renderer in an already merged map rather than touching the registry itself.
    // RenderNode is the single dispatch point every render passes through — the root, the children of
    // a column, a row or a table, the items of a lazy list — so substituting the values of the map
    // BEFORE it reaches the registry covers 100% of nodes without a line changing in the renderers.
public fun Map<KClass<out KompotComponent>, KompotComponentRenderer<out KompotComponent>>.withImpressionTracking(
    tracker: AnalyticsTracker,
    naming: KompotEventNamingRegistry,
): Map<KClass<out KompotComponent>, KompotComponentRenderer<out KompotComponent>> =
    mapValues { (_, renderer) ->
            @Suppress("UNCHECKED_CAST") // the same unchecked cast the registry's dispatch does
        ImpressionTrackingRenderer(renderer as KompotComponentRenderer<KompotComponent>, tracker, naming)
    }

private class ImpressionTrackingRenderer<T : KompotComponent>(
    private val delegate: KompotComponentRenderer<T>,
    private val tracker: AnalyticsTracker,
    private val naming: KompotEventNamingRegistry,
) : KompotComponentRenderer<T> {
    @Composable
    override fun Render(
        component: T,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
            // LaunchedEffect(component.id) fires once, when a node with this id enters the
            // composition — not on every recomposition. A repeat impression happens only if the
            // composable is genuinely recreated: the screen was left and returned to, or an item
            // scrolled out of a lazy list and back in. That is the correct meaning of "shown again".
        LaunchedEffect(component.id) {
            tracker.track(AnalyticsEvent.ComponentImpression(naming.describe(component)))
        }
        delegate.Render(component, actionHandler, formController)
    }
}
