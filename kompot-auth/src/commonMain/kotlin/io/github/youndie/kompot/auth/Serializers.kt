package io.github.youndie.kompot.auth

import io.github.youndie.kompot.KompotAction
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

public val kompotAuthSerializersModule: SerializersModule =
    SerializersModule {
        polymorphic(KompotAction::class) {
            subclass(UpdateSessionAction::class)
        }
    }
