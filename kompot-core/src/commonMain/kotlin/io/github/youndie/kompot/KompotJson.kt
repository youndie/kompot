package io.github.youndie.kompot

import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json

// The polymorphic bases are plain interfaces: no @Serializable, no generated serializer. A reified
// decodeFromString<KompotComponent>(body) therefore cannot be resolved at compile time, and what
// happens next depends on the platform rather than on the code — the JVM finds the polymorphic
// serializer by reflection and substitutes it silently, while Kotlin/Wasm and Kotlin/Native throw at
// the first response the server sends.
//
// That is a guarantee resting on a property of one platform that the contract never mentioned, so an
// implementer reading the contract could not know they were relying on it. These four exist so that
// nobody has to: the rule is now a function to call rather than a thing to know, and the failure it
// prevents waits until runtime — both targets compile, the JVM tests pass, the bundle builds and the
// page loads.
//
// SPEC.md §4.5 states the rule for implementations that are not written in Kotlin.

fun Json.decodeKompotComponent(body: String): KompotComponent = decodeFromString(PolymorphicSerializer(KompotComponent::class), body)

fun Json.encodeKompotComponent(component: KompotComponent): String = encodeToString(PolymorphicSerializer(KompotComponent::class), component)

fun Json.decodeKompotAction(body: String): KompotAction = decodeFromString(PolymorphicSerializer(KompotAction::class), body)

fun Json.encodeKompotAction(action: KompotAction): String = encodeToString(PolymorphicSerializer(KompotAction::class), action)
