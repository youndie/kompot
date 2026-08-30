package io.github.youndie.kompot.realtime.server

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Delivery to the subscribers of THIS instance, plus the bridge to the bus between instances.
//
// The division of responsibility matters: local channels are always local — a streaming connection
// physically lives on one machine — while the bus is responsible only for getting the event to every
// instance. So broadcast() delivers nothing directly; it publishes to the bus, and every instance,
// this one included, delivers once the message comes back. The path of an event is then the same
// wherever it originated, and "our own" subscribers need no separate handling.
//
// A subscriber is a Channel<String> carrying an already serialised payload rather than a transport's
// own event type: this module knows nothing about SSE or any HTTP framework, so it works just as well
// behind a WebSocket. Wrapping a payload into a transport event is the routing layer's business.
public class KompotUpdateBroadcaster(
    private val bus: KompotUpdateBus = InMemoryKompotUpdateBus(),
) {
    private val mutex = Mutex()
    private val subscribers = mutableMapOf<String, MutableSet<Channel<String>>>()

    // Whether the bus collector is running. This exists only to make the failure loud: a broadcaster
    // that was never started delivers NOTHING silently — broadcast() reaches the bus, and no one is
    // there to hand its messages out. The symptom ("updates just don't arrive") does not point at the
    // cause, so it is better to fail immediately and say why.
    private var started = false

    // The bus subscription lives as long as the scope passed in — an application's own background
    // scope. A separate method rather than the constructor: a constructor is no place to launch
    // coroutines, and a test is better off driving delivery by hand.
    public fun start(scope: CoroutineScope): Job {
        // UNDISPATCHED: the subscription must be in place BEFORE start() returns, or there is a window
        // between "started" and "actually subscribed" in which a published update is lost — the bus has
        // no replay and must not have one (see InMemoryKompotUpdateBus). At application start-up that is
        // a rare race; in a test it is a reliable failure.
        started = true
        return scope.launch(start = CoroutineStart.UNDISPATCHED) {
            bus.messages().collect { message ->
                deliverLocally(message.topic, message.payload)
            }
        }
    }

    public suspend fun subscribe(
        topic: String,
        channel: Channel<String>,
    ) {
        mutex.withLock { subscribers.getOrPut(topic) { mutableSetOf() }.add(channel) }
    }

    public suspend fun unsubscribe(
        topic: String,
        channel: Channel<String>,
    ) {
        mutex.withLock {
            val remaining = subscribers[topic]?.also { it.remove(channel) }
            // An empty set is not left behind: topics are per-subject, so without this the map would grow
            // with the number of subjects that ever connected.
            if (remaining != null && remaining.isEmpty()) subscribers.remove(topic)
        }
    }

    public suspend fun broadcast(
        topic: String,
        payload: String,
    ) {
        check(started) {
            "KompotUpdateBroadcaster.start(scope) was never called — the publish reaches the bus, but nothing will deliver it"
        }
        bus.publish(topic, payload)
    }

    // How many subscribers THIS instance holds. For tests and diagnostics: the number shows that
    // unsubscribing really happens instead of piling up.
    public suspend fun localSubscriberCount(topic: String): Int = mutex.withLock { subscribers[topic]?.size ?: 0 }

    private suspend fun deliverLocally(
        topic: String,
        payload: String,
    ) {
        val channels = mutex.withLock { subscribers[topic]?.toList() } ?: return
        channels.forEach { channel ->
            try {
                // trySend rather than send: a subscriber with a full buffer — a stalled client, a slow
                // network — must not hold up delivery to the rest, let alone block the bus collector.
                // Losing one update beats stalling everyone.
                channel.trySend(payload)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The channel is already closed: the subscriber left between the snapshot and the send.
            }
        }
    }
}
