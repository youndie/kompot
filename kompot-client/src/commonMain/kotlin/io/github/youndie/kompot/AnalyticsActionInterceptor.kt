package io.github.youndie.kompot

import io.github.youndie.kompot.analytics.AnalyticsEvent
import io.github.youndie.kompot.analytics.AnalyticsTracker
import io.github.youndie.kompot.analytics.KompotEventNamingRegistry

    // The mechanics here — always track, always proceed — do not depend on any application, which is
    // why this one lives in the toolkit while permission and navigation interceptors do not: those
    // are tied to an application's own notions of access and destinations.
public class AnalyticsActionInterceptor(
    private val tracker: AnalyticsTracker,
    private val naming: KompotEventNamingRegistry,
) : KompotActionInterceptor {
    override fun intercept(chain: KompotActionChain) {
        tracker.track(AnalyticsEvent.ActionTracked(naming.describe(chain.action)))
        chain.proceed()
    }
}
