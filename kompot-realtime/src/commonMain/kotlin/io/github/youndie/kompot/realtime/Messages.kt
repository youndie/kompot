package io.github.youndie.kompot.realtime

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import io.github.youndie.kompot.KompotComponent

// The update-channel protocol (see KompotRealtimeSource). The transport itself is not described
// here; this is only the frame contract, encoded in the data field of each ServerSentEvent.
// `component` is @Polymorphic: its serialiser comes from the shared SerializersModule, and this
// module knows nothing about concrete components.
//
// There used to be a sealed wrapper and a SubscribeMessage here, for protocol symmetry with a
// WebSocket transport where a client technically COULD send a frame back. SSE is one-way
// (server -> client) and the client physically cannot send anything after the handshake, so both
// became unreachable code and were removed: the one remaining frame type needs no sealed wrapper.
@Serializable
public data class UpdateComponentMessage(
    val componentId: String,
    val component: @Polymorphic KompotComponent,
)
