package io.github.youndie.kompot.analytics

import kotlin.test.Test
import kotlin.test.assertEquals

private class RecordingTestTracker : AnalyticsTracker {
    val received = mutableListOf<AnalyticsEvent>()

    override fun track(event: AnalyticsEvent) {
        received += event
    }
}

class CompositeAnalyticsTrackerTest {
    @Test
    fun `a single track call fans out to every wrapped tracker`() {
        val first = RecordingTestTracker()
        val second = RecordingTestTracker()
        val composite = CompositeAnalyticsTracker(listOf(first, second))
        val event = AnalyticsEvent.ScreenView("home")

        composite.track(event)

        assertEquals(listOf<AnalyticsEvent>(event), first.received)
        assertEquals(listOf<AnalyticsEvent>(event), second.received)
    }

    @Test
    fun `an empty tracker list is a no-op, not a crash`() {
        CompositeAnalyticsTracker(emptyList()).track(AnalyticsEvent.ScreenView("home"))
    }

    @Test
    fun `multiple track calls preserve order for every wrapped tracker`() {
        val tracker = RecordingTestTracker()
        val composite = CompositeAnalyticsTracker(listOf(tracker))

        composite.track(AnalyticsEvent.ScreenView("home"))
        composite.track(AnalyticsEvent.ScreenView("offer"))

        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.ScreenView("home"), AnalyticsEvent.ScreenView("offer")), tracker.received)
    }
}
