package io.github.youndie.kompot.analytics

// Not suspend, like the action handler and interceptor contracts. An implementation with real I/O —
// an HTTP analytics backend — arranges its own buffering and coroutines; that is not this contract's
// concern.
fun interface AnalyticsTracker {
    fun track(event: AnalyticsEvent)
}

class ConsoleAnalyticsTracker(
    private val tag: String = "[Analytics]",
) : AnalyticsTracker {
    override fun track(event: AnalyticsEvent) {
        println("$tag $event")
    }
}

// Fan-out to several trackers at once — a console in debug plus a real backend, say — without
// changing a single existing call site.
class CompositeAnalyticsTracker(
    private val trackers: List<AnalyticsTracker>,
) : AnalyticsTracker {
    override fun track(event: AnalyticsEvent) {
        trackers.forEach { it.track(event) }
    }
}
