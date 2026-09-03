package io.github.youndie.kompot.studio.edit

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Structural edits over the TEXT. Every assertion here is about a property a decode-and-re-encode
// implementation would fail: keeping an unfamiliar type, keeping a property this build does not
// declare, and keeping the formatting the file was committed with.
class JsonEditsTest {
    private val body =
        """
        {
          "type": "column",
          "id": "root",
          "children": [
            { "type": "text", "id": "first", "text": "one" },
            { "type": "text", "id": "second", "text": "two" },
            { "type": "esim_transfer_widget", "id": "third", "vendorOnlyField": 42 }
          ]
        }
        """.trimIndent()

    private fun children(text: String): List<String> =
        ((Json.parseToJsonElement(text) as JsonObject)["children"] as JsonArray).map {
            ((it as JsonObject)["id"] as JsonPrimitive).content
        }

    @Test
    fun `moving a node reorders it and rewrites nothing else`() {
        val moved = assertNotNull(JsonEdits.moveUp(body, "$.children[2]"))

        assertEquals(listOf("first", "third", "second"), children(moved))

        // The formatting claim, and it is the reason this is a splice rather than a re-serialise: the
        // document keeps its two-space indent, its spaces inside braces and its line breaks. A
        // prettyPrint round trip would rewrite every line and turn a reorder into a whole-file diff.
        assertEquals(body.lines().size, moved.lines().size)
        assertTrue(moved.contains("""    { "type": "text", "id": "first", "text": "one" },"""))

        // And nothing outside the array moved: the same characters before it.
        assertEquals(body.substringBefore("\"children\""), moved.substringBefore("\"children\""))
    }

    @Test
    fun `an unfamiliar type survives the move with the property nobody declares`() {
        val moved = assertNotNull(JsonEdits.moveUp(body, "$.children[2]"))

        // The whole argument for editing text. Decoding this body turns the third child into
        // UnknownComponent — the name is gone — and `ignoreUnknownKeys` drops vendorOnlyField on the
        // way in. Re-encoding would write back neither, and nothing would say so.
        assertTrue("esim_transfer_widget" in moved, "an unfamiliar type did not survive the edit")
        assertTrue("vendorOnlyField" in moved, "a property this build does not declare was dropped")
    }

    @Test
    fun `a duplicate gets an id of its own`() {
        val copied = assertNotNull(JsonEdits.duplicate(body, "$.children[0]"))

        assertEquals(listOf("first", "first-copy", "second", "third"), children(copied))
    }

    @Test
    fun `a duplicate renames the node's own id and not a child's`() {
        val nested =
            """{"type":"column","id":"root","children":[""" +
                """{"type":"row","id":"card","children":[{"type":"text","id":"inner","text":"x"}]}]}"""

        val copied = assertNotNull(JsonEdits.duplicate(nested, "$.children[0]"))
        val ids =
            ((Json.parseToJsonElement(copied) as JsonObject)["children"] as JsonArray).map {
                ((it as JsonObject)["id"] as JsonPrimitive).content
            }

        // The copy is `card-copy`, not a container whose CHILD got renamed — which is what a
        // search-and-replace over the node's text does, because the child's id comes first in it.
        assertEquals(listOf("card", "card-copy"), ids)
        assertEquals(2, Regex("\"inner\"").findAll(copied).count(), "the child's id was rewritten")
    }

    @Test
    fun `deleting takes the separator with it`() {
        listOf(0, 1, 2).forEach { index ->
            val after = assertNotNull(JsonEdits.delete(body, "$.children[$index]"))
            // Parses, which is the whole risk of a splice: a comma left behind or taken twice.
            assertEquals(2, children(after).size, "deleting child $index left a broken array")
        }

        val only = """{"type":"column","id":"root","children":[{"type":"text","id":"t","text":"x"}]}"""
        assertEquals(0, children(assertNotNull(JsonEdits.delete(only, "$.children[0]"))).size)
    }

    @Test
    fun `an edit that cannot apply says so instead of guessing`() {
        // Not an array element: a property is not one of a list, and moving it would mean rewriting
        // its name. The root likewise.
        assertNull(JsonEdits.moveUp(body, "$"))
        assertNull(JsonEdits.moveUp(body, "$.id"))
        // Already first, already last: there is no sibling to swap with, and inventing one would
        // corrupt the array.
        assertNull(JsonEdits.moveUp(body, "$.children[0]"))
        assertNull(JsonEdits.moveDown(body, "$.children[2]"))
        // A path that names nothing.
        assertNull(JsonEdits.delete(body, "$.children[9]"))
    }

    @Test
    fun `history remembers texts, and a new edit ends the redo line`() {
        val history = EditHistory("a")
        history.record("b")
        history.record("c")

        assertEquals("b", history.undo())
        assertEquals("a", history.undo())
        assertNull(history.undo())
        assertEquals("b", history.redo())

        history.record("d")
        // The future it would have returned to is not this text's future.
        assertTrue(!history.canRedo)
        assertEquals("b", history.undo())
    }
}
