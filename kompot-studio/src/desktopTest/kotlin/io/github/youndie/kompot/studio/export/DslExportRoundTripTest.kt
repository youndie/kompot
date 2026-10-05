package io.github.youndie.kompot.studio.export

import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.standard.TableRow
import io.github.youndie.kompot.standard.TextSpan
import io.github.youndie.kompot.studio.KompotStudioConfig
import io.github.youndie.kompot.studio.toolkitRegistry
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// A body with every field the exporter prints through the DSL set away from its default, so a field
// the export drops is a field the draft does not have. The root is named something other than `root`
// on purpose: `kompotScreen` cannot say that, and the children of a named container are numbered
// under its name rather than under the path above it. Paddings name all four sides because the
// builder writes an unnamed side as 0 where the wire leaves it out — the same padding, spelled
// differently, and pinned in DslExportTest rather than here.
internal val WITNESS_BODY =
    """
    { "type": "column", "id": "witness", "spacing": 8,
      "modifiers": [ { "type": "padding", "top": 4, "bottom": 0, "start": 0, "end": 0 } ],
      "alignment": "center", "arrangement": "space_between",
      "action": { "type": "navigate", "deeplink": "app://witness" },
      "accessibilityLabel": "The whole screen",
      "children": [
        { "type": "text", "id": "witness/0", "text": "Total: 12",
          "modifiers": [ { "type": "padding", "top": 0, "bottom": 0, "start": 8, "end": 0 } ],
          "style": "title_medium", "color": "on_surface",
          "spans": [
            { "text": "Total: " },
            { "text": "12", "style": "label_large", "color": "error", "action": { "type": "close" } }
          ],
          "maxLines": 2, "ellipsis": false, "heading": true },
        { "type": "button", "id": "pay", "text": "Pay",
          "modifiers": [ { "type": "padding", "top": 0, "bottom": 0, "start": 0, "end": 8 } ],
          "action": { "type": "close" }, "variant": "primary", "accessibilityLabel": "Pay twelve" },
        { "type": "row", "id": "rail", "spacing": 4,
          "modifiers": [ { "type": "padding", "top": 0, "bottom": 2, "start": 0, "end": 0 } ],
          "alignment": "end", "arrangement": "space_evenly", "scrollable": true,
          "action": { "type": "navigate", "deeplink": "app://rail" },
          "accessibilityLabel": "Open the rail",
          "children": [
            { "type": "text", "id": "rail/0", "text": "In the rail" },
            { "type": "text", "id": "root/2/1", "text": "Named like a path that is not its own" }
          ] },
        { "type": "column", "id": "witness/3", "spacing": 2,
          "modifiers": [ { "type": "padding", "top": 2, "bottom": 0, "start": 0, "end": 0 } ],
          "alignment": "start", "arrangement": "center",
          "action": { "type": "close" }, "accessibilityLabel": "Open the card",
          "children": [ { "type": "text", "id": "witness/3/0", "text": "In the card" } ] },
        { "type": "table", "id": "table",
          "modifiers": [ { "type": "padding", "top": 2, "bottom": 0, "start": 0, "end": 0 } ],
          "rows": [ { "cells": [ "Name", "Value" ], "header": true }, { "cells": [ "a", "b" ] } ] }
      ] }
    """.trimIndent()

// JSON → exported DSL → JSON, the studio's half of DslReachesTheWireTest (kompot-standard).
//
// That test asks whether the DSL can set every wire field. This one asks whether the EXPORT does: the
// draft of WITNESS_BODY is checked in as WitnessScreenDraft.kt and compiled with this source set, the
// exporter is held to producing it byte for byte, and here the compiled draft is run and compared with
// the body it was drafted from. A field the export drops comes out at its default and is named.
class DslExportRoundTripTest {
    private val config = KompotStudioConfig(registry = toolkitRegistry)

    // Defaults left out, so a field reads as present exactly when it says something.
    private val wire = Json(config.json) { encodeDefaults = false }

    private fun encode(component: KompotComponent): JsonElement =
        wire.encodeToJsonElement(PolymorphicSerializer(KompotComponent::class), component)

    private fun decode(body: String): KompotComponent =
        config.json.decodeFromString(PolymorphicSerializer(KompotComponent::class), body)

    @Test
    fun `the exporter still produces the witness draft that is checked in`() {
        val checkedIn = Path.of(System.getProperty("draft.witness")).readText()

        assertEquals(
            checkedIn,
            exportDsl(
                config,
                Json.parseToJsonElement(WITNESS_BODY),
                "io.github.youndie.kompot.studio.export",
                "witnessScreenDraft",
            ),
        )
    }

    @Test
    fun `the compiled witness draft carries every field of the body`() {
        val body = encode(decode(WITNESS_BODY))
        val drafted = encode(witnessScreenDraft())

        assertEquals(
            emptyList(),
            differences(body, drafted, "screen").distinct().sorted(),
            "fields where the compiled draft and the body disagree",
        )
    }

    // The witness is only as good as its coverage. Every type the exporter prints as a DSL call, and
    // the plain objects nested in them, must show up with every field its serializer has — so a field
    // added to TextComponent tomorrow turns this red until the witness sets it, and then the round trip
    // above turns red until the exporter prints it.
    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `the witness sets every field of every type the export prints through the DSL`() {
        val seen = mutableMapOf<String, MutableSet<String>>()
        collectKeys(encode(decode(WITNESS_BODY)), null, seen)

        val expected =
            DSL_CALLS.associateWith { type ->
                checkNotNull(wire.serializersModule.getPolymorphic(KompotComponent::class, type)) {
                    "no serializer for $type"
                }.descriptor.fieldNames() - "type"
            } +
                mapOf(
                    "table.rows" to TableRow.serializer().descriptor.fieldNames(),
                    "text.spans" to TextSpan.serializer().descriptor.fieldNames(),
                )

        val missing = expected.flatMap { (owner, fields) -> (fields - seen[owner].orEmpty()).map { "$owner.$it" } }
        assertEquals(emptyList(), missing.sorted(), "fields the witness does not set")
        assertTrue(seen.keys.containsAll(DSL_CALLS), "types the witness has no node of: ${DSL_CALLS - seen.keys}")
    }

    private fun SerialDescriptor.fieldNames(): Set<String> = (0 until elementsCount).map { getElementName(it) }.toSet()

    // Where two trees disagree, as `<type>.<field>`: what a reader of the failure needs is the name of
    // the field, not two pages of JSON that differ somewhere.
    private fun differences(
        expected: JsonElement?,
        actual: JsonElement?,
        owner: String,
    ): List<String> =
        when {
            expected is JsonObject && actual is JsonObject -> {
                val label = typeOf(expected) ?: owner
                (expected.keys + actual.keys).flatMap { key ->
                    val left = expected[key]
                    val right = actual[key]
                    when {
                        left is JsonObject && right is JsonObject -> {
                            differences(left, right, "$label.$key")
                        }

                        left is JsonArray && right is JsonArray && left.size == right.size -> {
                            left.indices.flatMap { differences(left[it], right[it], "$label.$key") }
                        }

                        left != right -> {
                            listOf("$label.$key")
                        }

                        else -> {
                            emptyList()
                        }
                    }
                }
            }

            expected != actual -> {
                listOf(owner)
            }

            else -> {
                emptyList()
            }
        }

    private fun collectKeys(
        element: JsonElement,
        owner: String?,
        seen: MutableMap<String, MutableSet<String>>,
    ) {
        if (element is JsonArray) element.forEach { collectKeys(it, owner, seen) }
        if (element is JsonObject) {
            val label = typeOf(element) ?: owner ?: return
            seen.getOrPut(label) { mutableSetOf() } += element.keys - "type"
            element.forEach { (key, value) -> collectKeys(value, "$label.$key", seen) }
        }
    }

    private fun typeOf(node: JsonObject): String? = (node["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
