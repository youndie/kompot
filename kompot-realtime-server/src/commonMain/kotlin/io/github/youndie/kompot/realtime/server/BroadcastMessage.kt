package io.github.youndie.kompot.realtime.server

import kotlinx.serialization.json.Json
import io.github.youndie.kompot.realtime.UpdateComponentMessage

// A typed wrapper over broadcast(topic, payload). The bus itself carries an opaque string (see
// KompotBusMessage), which is what keeps it ignorant of the protocol and unaffected by its evolution;
// an application, though, would rather hand over a message than serialise it at every call site.
suspend fun KompotUpdateBroadcaster.broadcast(
    topic: String,
    json: Json,
    message: UpdateComponentMessage,
) {
    broadcast(topic, json.encodeToString(UpdateComponentMessage.serializer(), message))
}
