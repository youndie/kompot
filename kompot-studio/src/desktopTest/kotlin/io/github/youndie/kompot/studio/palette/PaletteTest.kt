package io.github.youndie.kompot.studio.palette

import io.github.youndie.kompot.standard.TextComponent
import io.github.youndie.kompot.studio.diagnostics.Severity
import io.github.youndie.kompot.studio.diagnostics.diagnose
import io.github.youndie.kompot.studio.edit.JsonEdits
import io.github.youndie.kompot.studio.KompotStudioConfig
import io.github.youndie.kompot.studio.toolkitRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// The list of things a screen can be built from, and the node each of them drops in.
class PaletteTest {
    private val config = KompotStudioConfig(registry = toolkitRegistry)

    @Test
    fun `the palette is the profile's list, grouped by the module a type came from`() {
        val palette = paletteFor(config)

        val types = palette.map { it.wireType }
        assertTrue("text" in types)
        assertTrue("column" in types)
        // A form field is as addable as a text is: the palette is the profile, not a shortlist.
        assertTrue("text_input" in types, types.toString())

        // The grouping is the schema file, which is the spec module — a name a person recognises
        // rather than a package — and the two modules land in two groups rather than one heap.
        val standard = palette.first { it.wireType == "text" }.group
        val forms = palette.first { it.wireType == "text_input" }.group
        assertTrue(standard.contains("standard"), standard)
        assertTrue(forms != standard, "$forms and $standard should be separate groups")
        // Sorted, so the panel does not reshuffle itself between two builds of the same profile.
        assertEquals(palette.sortedWith(compareBy({ it.group }, { it.wireType })), palette)
    }

    @Test
    fun `a type with a sample is marked and a type without is not`() {
        val withSample =
            KompotStudioConfig(
                registry = toolkitRegistry,
                samples = listOf("text" to TextComponent(id = "sample", text = "hello")),
            )

        assertTrue(paletteFor(withSample).single { it.wireType == "text" }.hasSample)
        assertFalse(paletteFor(withSample).single { it.wireType == "column" }.hasSample)
        // The negative control: no samples at all, and nothing claims to have one.
        assertTrue(paletteFor(config).none { it.hasSample })
    }

    @Test
    fun `no profile means no palette rather than a guessed one`() {
        val blind = KompotStudioConfig(registry = toolkitRegistry, schemas = emptyMap())

        assertEquals(emptyList(), paletteFor(blind))
    }

    @Test
    fun `a sample is the node, whole`() {
        val withSample =
            KompotStudioConfig(
                registry = toolkitRegistry,
                samples = listOf("text" to TextComponent(id = "sample", text = "Order placed")),
            )

        val node = Json.parseToJsonElement(newNode(withSample, "text", "text_1")).jsonObject

        assertEquals("text", node.getValue("type").jsonPrimitive.content)
        // The words come with it. That is what a sample is for: a node that arrives visible.
        assertEquals("Order placed", node.getValue("text").jsonPrimitive.content)
    }

    @Test
    fun `a type without a sample arrives with its required fields and nothing invented`() {
        val node = Json.parseToJsonElement(newNode(config, "text", "text_1")).jsonObject

        assertEquals("text", node.getValue("type").jsonPrimitive.content)
        assertEquals("text_1", node.getValue("id").jsonPrimitive.content)
        // Required and a string, so it is there and it is empty — which is the state the diagnostics
        // layer is meant to talk about, not one this function should paper over with a placeholder.
        assertEquals("", node.getValue("text").jsonPrimitive.content)
        // Optional properties are absent rather than defaulted: a maxLines the studio invented would
        // be a decision nobody made travelling to a server as if somebody had.
        assertTrue("maxLines" !in node)
    }

    @Test
    fun `a container arrives empty rather than with an invented child`() {
        val node = Json.parseToJsonElement(newNode(config, "column", "column_1")).jsonObject

        assertEquals("column", node.getValue("type").jsonPrimitive.content)
        // `children` is required and a list, so it is written — as an empty one. A guessed child would
        // be a screen the studio wrote by itself.
        assertEquals(emptyList<JsonObject>(), node.getValue("children").let { it as? kotlinx.serialization.json.JsonArray }.orEmpty())
    }

    @Test
    fun `a node added without a sample turns up in the diagnostics until it is filled in`() {
        val added =
            assertNotNull(
                JsonEdits.insertInto(
                    """{ "type": "column", "id": "root", "children": [] }""",
                    "$",
                    "children",
                    0,
                    newNode(config, "text", "text_1"),
                    many = true,
                ),
            )

        val blank = diagnose(config, added).filter { it.layer == "draft" }
        assertEquals(1, blank.size, blank.toString())
        assertEquals("$.children[0]", blank.single().path)
        // A warning: the body is legal, and the person who dropped the node simply has not typed the
        // words yet. Calling it an error would put a red mark on every screen mid-edit.
        assertEquals(Severity.WARNING, blank.single().severity)

        // The positive control — the same node with its words in it says nothing at all, so the check
        // is reading the field rather than the fact that a text exists.
        val filled = added.replace(""""text": """"", """"text": "Order placed"""")
        assertTrue(diagnose(config, filled).none { it.layer == "draft" }, diagnose(config, filled).toString())
    }

    @Test
    fun `a type outside the profile still yields something parseable`() {
        // Not reachable from the palette, which only offers what the profile carries — but the
        // function takes a string, and a string can come from anywhere. The floor is a node that
        // parses and says what it is.
        val node = Json.parseToJsonElement(newNode(config, "no_such_type", "x")).jsonObject

        assertEquals("no_such_type", (node["type"] as JsonPrimitive).content)
    }
}
