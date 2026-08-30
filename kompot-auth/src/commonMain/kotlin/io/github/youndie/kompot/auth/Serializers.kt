package io.github.youndie.kompot.auth

import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import io.github.youndie.kompot.KompotAction

public val kompotAuthSerializersModule: SerializersModule =
    SerializersModule {
        polymorphic(KompotAction::class) {
            subclass(UpdateSessionAction::class)
        }
    }
