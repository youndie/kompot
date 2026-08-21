package io.github.youndie.kompot.spec

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// Every object of a JSON tree, the root included. Both the conformance checks and the corpus tests
// need it: a rule about a component — a non-empty id, a fieldId that resolves — applies to every
// node rather than to the root alone.
fun collectJsonObjects(element: JsonElement): List<JsonObject> =
    when (element) {
        is JsonObject -> listOf(element) + element.values.flatMap { collectJsonObjects(it) }
        is JsonArray -> element.flatMap { collectJsonObjects(it) }
        else -> emptyList()
    }
