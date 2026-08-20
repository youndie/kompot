package io.github.youndie.kompot.realtime.server

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// One bus message. `payload` is already-serialised JSON — normally an UpdateComponentMessage from
// :kompot-realtime. The bus must not know what is inside, or it stops being a transport and becomes
// part of the protocol.
data class KompotBusMessage(
    val topic: String,
    val payload: String,
)

// The update bus between server INSTANCES.
//
// Why it exists at all: a streaming connection always hangs off one particular instance, while the
// event that has to go into it happens on whichever instance the request landed on. With a single
// instance those are the same place, and a map in memory is enough. With more than one, the event and
// the subscriber end up on different machines, and without an external bus the update simply never
// reaches half the users.
//
// messages() deliberately returns EVERY message rather than a per-topic stream: topics are
// per-subject (`home:$userId`), there are as many of them as there are active users, and subscribing
// to each would mean one external subscription per user. One stream per process plus local routing by
// topic is orders of magnitude cheaper — in RedisKompotUpdateBus it is a single PSUBSCRIBE.
interface KompotUpdateBus {
    suspend fun publish(
        topic: String,
        payload: String,
    )

    // Messages from every instance, this one included: a subscriber must not have to tell its own
    // events from someone else's — delivery has to work the same either way.
    fun messages(): Flow<KompotBusMessage>
}

// The single-instance implementation, and the default: an application running as one local copy must
// not be made to require Redis.
class InMemoryKompotUpdateBus : KompotUpdateBus {
    // extraBufferCapacity rather than replay: a subscriber that connects later must not receive events
    // that happened before it subscribed — the screen was already rendered by the server with current
    // data, and replaying an old update would only corrupt it.
    private val flow = MutableSharedFlow<KompotBusMessage>(extraBufferCapacity = 64)

    override suspend fun publish(
        topic: String,
        payload: String,
    ) {
        flow.emit(KompotBusMessage(topic, payload))
    }

    override fun messages(): Flow<KompotBusMessage> = flow.asSharedFlow()
}
