package io.github.youndie.kompot

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.youndie.kompot.analytics.AnalyticsTracker

// Middleware for actions: a chain of responsibility. Instead of one global handler lambda, an action
// travels through an ordered list of independent interceptors — analytics, permission checks,
// routing. Each one decides whether to call chain.proceed() or to stop the chain, the way a
// permission interceptor does when access is refused.
public fun interface KompotActionInterceptor {
    public fun intercept(chain: KompotActionChain)
}

public interface KompotActionChain {
    public val action: KompotAction

        // Passes the same action on by default; an interceptor may substitute another one — a
        // wrapped, enriched version, say — by passing it explicitly.
    public fun proceed(action: KompotAction = this.action)
}

    // Folds the list of interceptors into a single handler, which is what component renderers
    // actually call.
public fun kompotActionHandler(interceptors: List<KompotActionInterceptor>): KompotActionHandler =
    KompotActionHandler { action -> RealKompotActionChain(interceptors, 0, action).proceed(action) }

private class RealKompotActionChain(
    private val interceptors: List<KompotActionInterceptor>,
    private val index: Int,
    override val action: KompotAction,
) : KompotActionChain {
    override fun proceed(action: KompotAction) {
            // The chain is exhausted: either the list was empty, or the last interceptor did not
            // call proceed() — it handled the action terminally.
        if (index >= interceptors.size) return

        val nextChain = RealKompotActionChain(interceptors, index + 1, action)
        interceptors[index].intercept(nextChain)
    }
}

public val LocalKompotActionHandler: ProvidableCompositionLocal<KompotActionHandler> =
    staticCompositionLocalOf<KompotActionHandler> {
        error("LocalKompotActionHandler not provided")
    }

public val LocalAnalyticsTracker: ProvidableCompositionLocal<AnalyticsTracker> =
    staticCompositionLocalOf<AnalyticsTracker> {
        error("LocalAnalyticsTracker not provided")
    }
