package io.github.youndie.kompot.realtime.redis

import io.lettuce.core.RedisClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assumptions.assumeTrue
import io.github.youndie.kompot.realtime.server.KompotBusMessage
import io.github.youndie.kompot.realtime.server.KompotUpdateBroadcaster
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// A run against a REAL Redis: replacing pub/sub with a fake here would prove nothing — what is under
// test is that a message crosses between processes, and that is a property of Redis, not of this code.
//
// Without REDIS_URL the test is skipped: an ordinary build must not require running infrastructure.
//
//   REDIS_URL=redis://localhost:6379 ./gradlew :kompot-realtime-redis:test
class RedisKompotUpdateBusTest {
    private val redisUrl: String? = System.getenv("REDIS_URL")
    private val clients = mutableListOf<RedisClient>()
    private val scopes = mutableListOf<CoroutineScope>()

    @AfterTest
    fun tearDown() {
        scopes.forEach { it.cancel() }
        clients.forEach { it.shutdown() }
    }

    private fun bus(prefix: String = "test:${System.nanoTime()}"): RedisKompotUpdateBus {
        val client = RedisClient.create(redisUrl!!).also { clients += it }
        return RedisKompotUpdateBus(client, channelPrefix = prefix)
    }

    private fun scope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scopes += it }

    // Subscribing in Redis is asynchronous: right after psubscribe the server may not have accepted it
    // yet, and a message published at that moment goes past. In production this does not matter — the
    // subscription lives as long as the process — but in a test it is a source of flakiness, so give it
    // time to settle.
    private suspend fun settle() = delay(300.milliseconds)

    @Test
    fun `a message published to redis comes back on the subscription`() {
        assumeTrue(redisUrl != null) { "REDIS_URL is not set — the run against a real Redis is skipped" }
        runBlocking {
            val bus = bus()
            val received = Channel<KompotBusMessage>(Channel.BUFFERED)
            scope().launch { bus.messages().collect { received.send(it) } }
            settle()

            bus.publish("home:user1", "payload")

            val message = withTimeout(5.seconds) { received.receive() }
            assertEquals("home:user1", message.topic)
            assertEquals("payload", message.payload)
        }
    }

    // The property this whole module exists for: an event published on ONE instance reaches a
    // subscriber on ANOTHER. Two independent Redis clients are exactly two processes.
    @Test
    fun `an update published on one instance reaches a subscriber on another`() {
        assumeTrue(redisUrl != null) { "REDIS_URL is not set — the run against a real Redis is skipped" }
        runBlocking {
            val prefix = "test:${System.nanoTime()}"
            val instanceA = bus(prefix)
            val instanceB = bus(prefix)

            val delivered = Channel<String>(Channel.BUFFERED)
            val broadcasterB = KompotUpdateBroadcaster(instanceB)
            broadcasterB.start(scope())
            val subscriber = Channel<String>(Channel.BUFFERED)
            broadcasterB.subscribe("home:user1", subscriber)
            scope().launch { subscriber.consumeEachInto(delivered) }
            settle()

            // The request landed on instance A while the streaming connection hangs off instance B.
            KompotUpdateBroadcaster(instanceA).broadcast("home:user1", "payload-from-A")

            assertEquals("payload-from-A", withTimeout(5.seconds) { delivered.receive() })
        }
    }

    // A topic contains colons (`home:$userId`): exactly the channel prefix is stripped, not everything
    // up to the first separator, or the addressee of an update would change in flight.
    @Test
    fun `a topic containing colons survives the round trip`() {
        assumeTrue(redisUrl != null) { "REDIS_URL is not set — the run against a real Redis is skipped" }
        runBlocking {
            val bus = bus()
            val received = Channel<KompotBusMessage>(Channel.BUFFERED)
            scope().launch { bus.messages().collect { received.send(it) } }
            settle()

            bus.publish("updates:user-42:page:1", "payload")

            assertEquals("updates:user-42:page:1", withTimeout(5.seconds) { received.receive() }.topic)
        }
    }

    // One Redis is usually shared between environments: events from one must not reach another.
    @Test
    fun `buses with different channel prefixes do not see each other`() {
        assumeTrue(redisUrl != null) { "REDIS_URL is not set — the run against a real Redis is skipped" }
        runBlocking {
            val dev = bus("test:dev:${System.nanoTime()}")
            val prod = bus("test:prod:${System.nanoTime()}")
            val received = Channel<KompotBusMessage>(Channel.BUFFERED)
            scope().launch { prod.messages().collect { received.send(it) } }
            settle()

            dev.publish("home:user1", "dev-only")
            delay(500.milliseconds)

            assertNull(received.tryReceive().getOrNull(), "an event with another prefix reached the subscriber")
        }
    }
}

private suspend fun Channel<String>.consumeEachInto(target: Channel<String>) {
    for (value in this) target.send(value)
}
