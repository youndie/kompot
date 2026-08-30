package io.github.youndie.kompot.interop

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent

// Swift cannot reach a component's or an action's wire type tag through KClass reflection the way a
// Compose client does, keyed by KClass<out KompotComponent>: Kotlin/Native does not export that kind
// of reflection to Swift in any usable form.
//
// So the tag is read from the same @SerialName every concrete type already carries, through
// SerializersModule.getPolymorphic — the very machinery the JSON encoder uses for the "type"
// discriminator. A renderer on the Swift side can then dispatch on that string through an ordinary
// [String: Renderer] dictionary.
@OptIn(ExperimentalSerializationApi::class)
public fun kompotComponentTypeName(
    component: KompotComponent,
    json: Json,
): String =
    json.serializersModule.getPolymorphic(KompotComponent::class, component)?.descriptor?.serialName
        ?: error("No serializer registered for ${component::class}")

@OptIn(ExperimentalSerializationApi::class)
public fun kompotActionTypeName(
    action: KompotAction,
    json: Json,
): String =
    json.serializersModule.getPolymorphic(KompotAction::class, action)?.descriptor?.serialName
        ?: error("No serializer registered for ${action::class}")
