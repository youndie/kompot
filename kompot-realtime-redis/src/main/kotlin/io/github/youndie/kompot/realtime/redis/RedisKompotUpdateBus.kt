package io.github.youndie.kompot.realtime.redis

import io.lettuce.core.RedisClient
import io.lettuce.core.pubsub.RedisPubSubAdapter
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.future.await
import io.github.youndie.kompot.realtime.server.KompotBusMessage
import io.github.youndie.kompot.realtime.server.KompotUpdateBus

// The update bus over Redis pub/sub — what makes more than one server instance possible.
//
// The decision that matters: ONE PSUBSCRIBE per process over the pattern "<prefix>:*", not a
// SUBSCRIBE per topic. Topics are per-subject (`home:$userId`), there are as many of them as there
// are active users, and subscribing to each would mean thousands of subscriptions and a constant
// stream of SUBSCRIBE/UNSUBSCRIBE as users come and go. Routing by topic is cheaper and is done
// locally (see KompotUpdateBroadcaster).
//
// Redis is chosen as pub/sub WITHOUT delivery guarantees, deliberately: a component update is a thing
// you can afford to lose — the client gets the current state with its next screen request anyway —
// while a queue with guarantees would cost separate infrastructure. Anything that must not be lost
// travels through an outbox instead, not through here.
public class RedisKompotUpdateBus(
    private val client: RedisClient,
    // The channel prefix. A parameter of its own because one Redis is usually shared between several
    // applications and environments: without a prefix, events from one would reach subscribers of
    // another.
    private val channelPrefix: String = "kompot:updates",
) : KompotUpdateBus {
    // Separate connections for publishing and subscribing — a requirement of the protocol: a
    // connection that has entered subscribe mode serves no ordinary commands.
    private val publisher: StatefulRedisPubSubConnection<String, String> by lazy { client.connectPubSub() }

    override suspend fun publish(
        topic: String,
        payload: String,
    ) {
        publisher.async().publish(channelFor(topic), payload).await()
    }

    override fun messages(): Flow<KompotBusMessage> =
        callbackFlow {
            val connection = client.connectPubSub()
            val listener =
                object : RedisPubSubAdapter<String, String>() {
                    override fun message(
                        pattern: String,
                        channel: String,
                        message: String,
                    ) {
                        // trySendBlocking rather than send: the driver invokes this callback on its own
                        // I/O thread, which must not be suspended.
                        trySendBlocking(KompotBusMessage(topic = topicOf(channel), payload = message))
                    }
                }
            connection.addListener(listener)
            connection.async().psubscribe("$channelPrefix:*")

            awaitClose {
                connection.removeListener(listener)
                connection.close()
            }
        }

    public fun close() {
        publisher.close()
        client.shutdown()
    }

    public companion object {
        // A factory from a URL: an application picks a bus backend, not a Redis library, and needs no
        // knowledge of which client is inside. It is also the only place a RedisClient is created, which
        // is why closing it lives here too.
        public fun create(
            url: String,
            channelPrefix: String = "kompot:updates",
        ): RedisKompotUpdateBus = RedisKompotUpdateBus(RedisClient.create(url), channelPrefix)
    }

    private fun channelFor(topic: String) = "$channelPrefix:$topic"

    // A topic may contain colons (`home:$userId`), so exactly the prefix is stripped, not everything up
    // to the first separator.
    private fun topicOf(channel: String) = channel.removePrefix("$channelPrefix:")
}
