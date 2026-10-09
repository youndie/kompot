package io.github.youndie.kompot.commands

import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.realtime.UpdateComponentMessage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// An answer that replaces nodes of the screen by id instead of sending the screen again (SPEC.md
// §16.4). Before it, the only answers that changed what a screen shows were `navigate` and `refresh`,
// both a whole tree: a filter that changes one list, a badge after a perform — every one of them cost
// the screen, and a tree the server had to know how to rebuild from nothing.
//
// No new shape of a node and no new way to address one: the frames are the update channel's own
// (§10), delivered by an answer rather than by the channel, and they go into the same override store.
// A selector language ("the list after the header") was rejected for that reason — the id already is
// the address, and every implementation would have read a selector its own way.
//
// `deeplink` and `history` say what address the screen has now. The server knows it, because the
// server computed the state; the toolkit hands it to the application, which keeps the history
// (§12). They are on `update` rather than on `load` because a submit that changes the address has no
// `load` in it.
@Serializable
@SerialName("update")
public data class UpdateAction(
    /** The nodes to replace, in order: of two frames for one id, the later wins; an id the screen does not have is ignored (§10.2). */
    val updates: List<UpdateComponentMessage>,
    /** The address the screen has after the update, by the rules of `navigate.deeplink` (§12.2); absent leaves the address as it was. */
    val deeplink: String? = null,
    /** How [deeplink] enters the application's history: `push` (default) or `replace`. An open string; an unfamiliar word means `push`. */
    val history: String? = null,
) : KompotAction

// The words `history` takes, as constants for whoever writes an update in Kotlin. The wire keeps it an
// open string: these are the ones this toolkit's client understands, not a closed set a newer server
// is held to.
public object UpdateHistory {
    public const val PUSH: String = "push"
    public const val REPLACE: String = "replace"

    /** The word a client acts on: [REPLACE] when the server said so, [PUSH] for anything else, absence and an unknown word included. */
    public fun of(word: String?): String = if (word == REPLACE) REPLACE else PUSH
}

// `GET url`, whose answer is an action run through the whole chain (SPEC.md §16.4) — the reading
// sibling of `perform`. A filter chip, a tab of results, "show more like this": a press that changes
// nothing on the server and wants an answer, usually an `update`.
//
// Its own endpoint kind, `load` (§16.1), and not a `submit` sent with GET: a client chooses how to
// call by the kind, and a submit is a POST with an Idempotency-Key (§16.5). Nothing changes, so there
// is no key, the answer may be cached, a repeat is safe and the conformance kit can walk the kind
// blind.
//
// Of two loads pressed on the same screen the later wins: the answer to the earlier is dropped. Filters
// are pressed one after another and the network does not keep their order.
@Serializable
@SerialName("load")
public data class LoadAction(
    /** The relative address of an endpoint of kind `load`: GET, answered with an action. */
    val url: String,
) : KompotAction
