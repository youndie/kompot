package io.github.youndie.kompot.tck

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.utils.io.readLine
import kotlinx.coroutines.withTimeoutOrNull

public data class TckResponse(
    val status: Int,
    val headers: Map<String, String>,
    val body: String,
) {
    // HTTP header names are case-insensitive while the checks compare them by name, so the case is
    // normalised once here instead of in every check.
    public fun header(name: String): String? = headers[name.lowercase()]
}

// What came out of a streaming endpoint while somebody listened to it. The text is raw — every byte
// the server wrote — and is read by TckEventStream, the same parser a recording goes through: what a
// frame must look like is one rule, and a live capture must not get a second, kinder reader.
public data class TckStreamCapture(
    val status: Int,
    val body: String,
)

// The only thing the checks know about transport. That is what lets one set of them run against a
// server started in-process and against somebody else's address over the network alike.
public interface TckTransport {
    public suspend fun request(
        method: String,
        path: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
    ): TckResponse

    // LISTENING rather than asking: connect, hold the connection open for a window, and hand back
    // everything that arrived. One check needs this and only one — who receives which topic cannot be
    // answered by a recording, because a recording is one subscriber's stream and the question is
    // about the OTHER subscriber (SPEC.md §16.9).
    //
    // null rather than an exception, and a default rather than a new member every implementation must
    // write: a transport that cannot listen — an in-process one built for request/response — stays
    // valid, and the check says out loud that it was not run instead of passing.
    public suspend fun stream(
        path: String,
        headers: Map<String, String> = emptyMap(),
        windowMillis: Long = 3_000,
    ): TckStreamCapture? = null

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

    // Read incrementally and cut off by the clock: a stream of updates has no end of its own, so
    // anything that reads "the whole body" either hangs or, when the timeout cancels it, throws away
    // the very bytes that were the answer. What arrived before the window closed is the answer, an
    // empty capture included.
    override suspend fun stream(
        path: String,
        headers: Map<String, String>,
        windowMillis: Long,
    ): TckStreamCapture {
        val captured = StringBuilder()
        var status = 0

        client
            .prepareRequest(baseUrl.trimEnd('/') + path) {
                this.method = HttpMethod.Get
                headers.forEach { (name, value) -> header(name, value) }
                header(HttpHeaders.Accept, ContentType.Text.EventStream.toString())
            }.execute { response ->
                status = response.status.value
                val channel = response.bodyAsChannel()
                withTimeoutOrNull(windowMillis) {
                    while (!channel.isClosedForRead) {
                        val line = channel.readLine() ?: break
                        captured.append(line).append('\n')
                    }
                }
            }

        return TckStreamCapture(status, captured.toString())
    }

    override suspend fun close(): Unit = client.close()
}
