package io.github.youndie.kompot.studio

import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.standard.NavigateAction

// WHAT THE SCREEN WOULD HAVE DONE. A preview hands renderers an action handler that does nothing —
// it must, since a tap has nowhere to go — and the consequence is that the half of a screen made of
// actions is invisible: a button wired to the wrong deeplink looks exactly like one wired correctly.
// The log is that half, written down.
internal data class LoggedAction(
    val at: String,
    val action: KompotAction,
) {
    val text: String get() = "$at  $action"

    // The one action the studio can act on: a deeplink names a route of the graph, and the graph is
    // what an HTTP source already read. Everything else — opening a URL, submitting a form, copying
    // text — is written down and nothing more, because doing it would mean the studio performing a
    // side effect on somebody's behalf from a screen nobody has shipped.
    val deeplink: String? get() = (action as? NavigateAction)?.deeplink
}
