package io.github.youndie.kompot.ktor

import io.ktor.http.ContentType
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import io.github.youndie.kompot.KompotComponent

// KompotComponent is an open interface, not @Serializable, so a plain call.respond(component)
// serialises it through the CONCRETE runtime class's serialiser: ContentNegotiation resolves by the
// static type reflectively rather than through a nested @Polymorphic field. The root node then loses
// its "type" discriminator in the JSON and the client cannot tell which component it is (see
// UnknownComponent and the default deserializer). Nested children serialise fine, because they go
// through List<@Polymorphic KompotComponent>. This helper serialises the root explicitly through
// PolymorphicSerializer so that "type" is present there too.
suspend fun ApplicationCall.respondKompotComponent(
    json: Json,
    component: KompotComponent,
) {
    respondText(json.encodeToString(PolymorphicSerializer(KompotComponent::class), component), ContentType.Application.Json)
}
