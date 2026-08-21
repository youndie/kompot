package io.github.youndie.kompot.interop

import kotlinx.serialization.json.Json
import io.github.youndie.kompot.realtime.UpdateComponentMessage

// UpdateComponentMessage is not polymorphic itself — a plain @Serializable data class, like
// KompotFormResponse — only its `component` is, and that is settled by the SerializersModule of the
// Json passed in. All that is needed here is a non-generic wrapper over the reified
// decodeFromString<T>(), which is not exported to Swift.
fun decodeUpdateComponentMessage(
    json: Json,
    text: String,
): UpdateComponentMessage = json.decodeFromString(UpdateComponentMessage.serializer(), text)
