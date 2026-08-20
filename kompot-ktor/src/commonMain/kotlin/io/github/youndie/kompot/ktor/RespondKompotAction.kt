package io.github.youndie.kompot.ktor

import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import io.github.youndie.kompot.KompotAction

// The same problem and the same fix as respondKompotComponent: KompotAction is an open interface,
// not @Serializable, so a root call.respond(action) resolves the concrete runtime class's serialiser
// and loses the "type" discriminator. Needed when the server answers a client action with an action
// rather than with a component tree — for instance to tell the client about a new session after a
// successful login.
suspend fun ApplicationCall.respondKompotAction(
    json: Json,
    action: KompotAction,
) {
    respondText(json.encodeToString(PolymorphicSerializer(KompotAction::class), action), ContentType.Application.Json)
}
