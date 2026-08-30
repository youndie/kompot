package io.github.youndie.kompot.tck

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType

public data class TckResponse(
    val status: Int,
    val headers: Map<String, String>,
    val body: String,
) {
    // HTTP header names are case-insensitive while the checks compare them by name, so the case is
    // normalised once here instead of in every check.
    public fun header(name: String): String? = headers[name.lowercase()]
}

// The only thing the checks know about transport. That is what lets one set of them run against a
// server started in-process and against somebody else's address over the network alike.
public interface TckTransport {
    public suspend fun request(
        method: String,
        path: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
    ): TckResponse

    public suspend fun close(): Unit = Unit
}

// The transport to an external server. This is the one a team implementing a server on another stack
// actually uses.
public class RemoteTckTransport(
    private val baseUrl: String,
    private val client: HttpClient = HttpClient(CIO),
) : TckTransport {
    override suspend fun request(
        method: String,
        path: String,
        headers: Map<String, String>,
        body: String?,
    ): TckResponse {
        val response =
            client.request(baseUrl.trimEnd('/') + path) {
                this.method = HttpMethod.parse(method)
                headers.forEach { (name, value) -> header(name, value) }
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            }

        return TckResponse(
            status = response.status.value,
            headers = response.headers.entries().associate { it.key.lowercase() to it.value.first() },
            body = response.bodyAsText(),
        )
    }

    override suspend fun close(): Unit = client.close()
}
