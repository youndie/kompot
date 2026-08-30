package io.github.youndie.kompot.commands

import io.github.youndie.kompot.KompotAction
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

public val kompotCommandsSerializersModule: SerializersModule =
    SerializersModule {
        polymorphic(KompotAction::class) {
            subclass(PerformAction::class)
        }
    }
