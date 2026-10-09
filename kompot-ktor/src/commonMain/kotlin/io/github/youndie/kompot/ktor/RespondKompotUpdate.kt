package io.github.youndie.kompot.ktor

import io.github.youndie.kompot.commands.UpdateBuilder
import io.github.youndie.kompot.commands.kompotUpdate
import io.ktor.server.application.ApplicationCall
import kotlinx.serialization.json.Json

/**
 * Answers with an `update` (SPEC.md §16.4) written with the screen DSL: the nodes [block] adds replace
 * the nodes with the same ids on the client's screen, and [deeplink] with [history] — one of
 * `UpdateHistory` — is the address the screen has afterwards. The usual answer of a `load` endpoint and
 * of a `perform` whose result is a few nodes rather than a screen.
 *
 * The server is the one who knows the address, so it is also the one who must serve [deeplink]: the
 * screen fetched by it SHOULD equal what the client shows after this update, or back, reload and a
 * shared link show something the person never saw.
 *
 * A client that predates `update` does nothing with it (§15): answer such a client with `refresh` or a
 * `navigate` to the screen instead.
 */
public suspend fun ApplicationCall.respondKompotUpdate(
    json: Json,
    deeplink: String? = null,
    history: String? = null,
    block: UpdateBuilder.() -> Unit,
) {
    respondKompotAction(json, kompotUpdate(deeplink, history, block))
}
