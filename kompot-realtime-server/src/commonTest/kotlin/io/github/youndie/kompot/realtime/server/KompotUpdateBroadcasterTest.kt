package io.github.youndie.kompot.realtime.server

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

// Delivery is checked without any infrastructure: who delivers to whom, and who is no longer owed
// anything, does not depend on what connects the instances. Redis is checked separately, against a
// real Redis — see RedisKompotUpdateBusTest.
//
// runBlocking rather than runTest: what is under test is REAL concurrent delivery (the bus collector
// lives in its own coroutine), and runTest's virtual time only gets in the way — its withTimeout fires
// before the collector is ever scheduled.
private suspend fun Channel<String>.receiveSoon(): String = withTimeout(2.seconds) { receive() }

// A scope per test: the bus collector is endless and has to be cancelled explicitly, or the test never
// finishes.
private fun broadcasterTest(block: suspend (KompotUpdateBroadcaster, KompotUpdateBus) -> Unit) =
    runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val bus = InMemoryKompotUpdateBus()
        val broadcaster = KompotUpdateBroadcaster(bus)
        broadcaster.start(scope)
        try {
            block(broadcaster, bus)
        } finally {
            scope.cancel()
        }
    }

class KompotUpdateBroadcasterTest {
    @Test
    fun `a subscriber receives an update published to its topic`() =
        broadcasterTest { broadcaster, _ ->
            val channel = Channel<String>(Channel.BUFFERED)
            broadcaster.subscribe("home:user1", channel)

            broadcaster.broadcast("home:user1", """{"componentId":"summary"}""")

            assertEquals("""{"componentId":"summary"}""", channel.receiveSoon())
        }

    // Topics are per-subject, and that is the whole privacy guarantee: one user's update must not
    // arrive on another user's screen.
    @Test
    fun `an update never reaches a subscriber of another topic`() =
        broadcasterTest { broadcaster, _ ->
            val mine = Channel<String>(Channel.BUFFERED)
            val other = Channel<String>(Channel.BUFFERED)
            broadcaster.subscribe("home:user1", mine)
            broadcaster.subscribe("home:user2", other)

            broadcaster.broadcast("home:user1", "payload")

            assertEquals("payload", mine.receiveSoon())
            assertNull(other.tryReceive().getOrNull(), "the update reached a subscriber of another topic")
        }

    @Test
    fun `every subscriber of the same topic gets the update`() =
        broadcasterTest { broadcaster, _ ->
            val phone = Channel<String>(Channel.BUFFERED)
            val desktop = Channel<String>(Channel.BUFFERED)
            broadcaster.subscribe("home:user1", phone)
            broadcaster.subscribe("home:user1", desktop)

            broadcaster.broadcast("home:user1", "payload")

            assertEquals("payload", phone.receiveSoon())
            assertEquals("payload", desktop.receiveSoon())
        }

    @Test
    fun `an unsubscribed channel stops receiving`() =
        broadcasterTest { broadcaster, _ ->
            val channel = Channel<String>(Channel.BUFFERED)
            broadcaster.subscribe("home:user1", channel)
            broadcaster.unsubscribe("home:user1", channel)

            broadcaster.broadcast("home:user1", "payload")

            assertNull(channel.tryReceive().getOrNull())
        }

    // There are as many topics as users: without sweeping empty sets the map would grow with the
    // number of users that ever connected.
    @Test
    fun `the topic is forgotten once its last subscriber leaves`() =
        broadcasterTest { broadcaster, _ ->
            val channel = Channel<String>(Channel.BUFFERED)
            broadcaster.subscribe("home:user1", channel)
            assertEquals(1, broadcaster.localSubscriberCount("home:user1"))

            broadcaster.unsubscribe("home:user1", channel)

            assertEquals(0, broadcaster.localSubscriberCount("home:user1"))
        }

    // Our own events come back through the bus instead of being delivered directly: otherwise the path
    // of an event would depend on which instance it arose on, and the multi-instance case would differ
    // from the single-instance one.
    @Test
    fun `a publish made directly on the bus reaches local subscribers too`() =
        broadcasterTest { broadcaster, bus ->
            val channel = Channel<String>(Channel.BUFFERED)
            broadcaster.subscribe("home:user1", channel)

            // This is what a message from ANOTHER instance looks like.
            bus.publish("home:user1", "from-another-instance")

            assertEquals("from-another-instance", channel.receiveSoon())
        }

    // A stalled subscriber — a full buffer — must not hold up delivery to the rest.
    @Test
    fun `a full channel does not block delivery to the others`() =
        broadcasterTest { broadcaster, _ ->
            val stuck = Channel<String>(capacity = 1)
            val healthy = Channel<String>(Channel.BUFFERED)
            broadcaster.subscribe("home:user1", stuck)
            broadcaster.subscribe("home:user1", healthy)
            stuck.send("already taken")

            broadcaster.broadcast("home:user1", "payload")

            assertEquals("payload", healthy.receiveSoon())
        }
}
