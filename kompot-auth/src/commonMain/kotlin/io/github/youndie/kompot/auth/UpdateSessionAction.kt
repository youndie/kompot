package io.github.youndie.kompot.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import io.github.youndie.kompot.KompotAction

// The only type in this module: the action a server uses to hand the client a new session, most
// often right after a successful login. The module knows nothing about token storage or session
// management — this is a description of the wire, and the handler that acts on it belongs to the
// application, where server-driven UI and authentication meet.
@Serializable
@SerialName("update_session")
public data class UpdateSessionAction(
    val accessToken: String,
    val refreshToken: String,
) : KompotAction
