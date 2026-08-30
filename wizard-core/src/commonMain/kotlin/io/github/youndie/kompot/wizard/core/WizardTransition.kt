package io.github.youndie.kompot.wizard.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// @Serializable rather than strictly zero-dependency, on the same principle as kompot-core and
// form-core: "clean" in this toolkit means "knows nothing about a saga engine, server-driven UI, a UI
// framework or an HTTP server", not "knows nothing about kotlinx.serialization". Without it, carrying
// a client's transition to the server over HTTP would need a DTO layer of its own for no real gain.
// WizardTransition is usable anywhere — HTTP, a chat bot, a console REPL — and serialisation is not
// needed by every consumer, but it is in the way of none.
//
// @SerialName is mandatory. Without it the wire discriminator becomes the FULL Kotlin class name, so
// the package name of this implementation leaks into the protocol and renaming or moving the class
// breaks compatibility with released clients and with server implementations on other stacks. Short
// names are the same style as the rest of the toolkit's wire types. This module has already been
// through that once: the transitions used to travel as their Kotlin FQNs.
@Serializable
public sealed interface WizardTransition {
    @Serializable
    @SerialName("next")
    public data object Next : WizardTransition

    @Serializable
    @SerialName("back")
    public data object Back : WizardTransition

    @Serializable
    @SerialName("jump_to")
    public data class JumpTo(
        val stepId: String,
    ) : WizardTransition

    @Serializable
    @SerialName("finish")
    public data object Finish : WizardTransition
}
