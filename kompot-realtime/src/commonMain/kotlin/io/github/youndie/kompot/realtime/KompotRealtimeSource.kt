package io.github.youndie.kompot.realtime

import kotlinx.coroutines.flow.Flow

// The contract for fetching live component updates, mirroring KompotPageLoader in :kompot-standard:
// a description of the wire with no transport detail. A concrete SSE implementation lives in the
// application, not here — neither kompot-realtime nor kompot-client should know which HTTP client
// is in use.
public fun interface KompotRealtimeSource {
    public fun subscribe(topic: String): Flow<UpdateComponentMessage>
}
