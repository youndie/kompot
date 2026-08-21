package io.github.youndie.kompot

import io.github.youndie.kompot.analytics.AnalyticsEvent
import io.github.youndie.kompot.analytics.AnalyticsTracker
import io.github.youndie.kompot.analytics.KompotActionEventNaming
import io.github.youndie.kompot.analytics.KompotEventDescriptor
import io.github.youndie.kompot.analytics.KompotEventNamingRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private data class InterceptorTestAction(
    val tag: String,
) : KompotAction

class AnalyticsActionInterceptorTest {
    @Test
    fun `tracks an ActionTracked event using the naming registry then always proceeds`() {
        val tracked = mutableListOf<AnalyticsEvent>()
        val tracker = AnalyticsTracker { tracked += it }
        val naming =
            KompotEventNamingRegistry(
                actionNaming =
                    mapOf(
                        InterceptorTestAction::class to
                            KompotActionEventNaming { a -> KompotEventDescriptor("tap", mapOf("tag" to (a as InterceptorTestAction).tag)) },
                    ),
            )
        var reachedEnd = false
        val handler =
            kompotActionHandler(
                listOf(
                    AnalyticsActionInterceptor(tracker, naming),
                    KompotActionInterceptor { reachedEnd = true },
                ),
            )

        handler.handle(InterceptorTestAction("submit"))

        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.ActionTracked(KompotEventDescriptor("tap", mapOf("tag" to "submit")))), tracked)
        assertTrue(reachedEnd)
    }

    @Test
    fun `an unregistered action still tracks via the class-name fallback`() {
        val tracked = mutableListOf<AnalyticsEvent>()
        val tracker = AnalyticsTracker { tracked += it }
        val handler = kompotActionHandler(listOf(AnalyticsActionInterceptor(tracker, KompotEventNamingRegistry())))

        handler.handle(InterceptorTestAction("submit"))

        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.ActionTracked(KompotEventDescriptor("InterceptorTestAction"))), tracked)
    }
}
