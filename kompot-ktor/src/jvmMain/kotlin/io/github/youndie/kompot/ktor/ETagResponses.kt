package io.github.youndie.kompot.ktor

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import io.github.youndie.kompot.KompotComponent
import java.security.MessageDigest

// java.security.MessageDigest is a JVM-specific API, so this whole file lives in jvmMain rather than
// in commonMain where respondKompotComponent sits — even though kompot-ktor has one real platform
// today.

private fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

// Wraps respondKompotComponent in a conditional response: computes an ETag over the already
// serialised body, compares it with If-None-Match and answers 304 with no body when the client
// already holds the current one; otherwise sends the body as usual, with an ETag header.
suspend fun ApplicationCall.respondKompotComponentCached(
    json: Json,
    component: KompotComponent,
) {
    respondWithETag(json.encodeToString(PolymorphicSerializer(KompotComponent::class), component))
}

// The same, but for ordinary @Serializable types, which need no polymorphic-serialiser detour —
// that exists in respondKompotComponent only for a polymorphic root.
suspend inline fun <reified T> ApplicationCall.respondCached(
    json: Json,
    value: T,
) {
    respondWithETag(json.encodeToString(value))
}

suspend fun ApplicationCall.respondWithETag(body: String) {
    val etag = "\"" + sha256Hex(body.toByteArray()) + "\""
    if (request.header(HttpHeaders.IfNoneMatch) == etag) {
        response.header(HttpHeaders.ETag, etag)
        respond(HttpStatusCode.NotModified)
        return
    }
    response.header(HttpHeaders.ETag, etag)
    respondText(body, ContentType.Application.Json)
}
