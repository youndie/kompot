package io.github.youndie.kompot.ktor

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receiveText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.decodeKompotComponent

// The inbound mirror of respondKompotComponent: KompotComponent is an open interface, not
// @Serializable, so a plain call.receive<KompotComponent>() cannot resolve the concrete runtime
// class's serialiser by itself (see the comment in RespondKompotComponent.kt) — the body has to be
// decoded explicitly through PolymorphicSerializer.
public suspend fun ApplicationCall.receiveKompotComponent(json: Json): KompotComponent =
    try {
        json.decodeKompotComponent(receiveText())
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        throw BadRequestException("Failed to parse KompotComponent: ${e.message}")
    }
