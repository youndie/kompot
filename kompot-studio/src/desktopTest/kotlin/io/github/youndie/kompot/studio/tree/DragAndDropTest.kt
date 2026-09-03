package io.github.youndie.kompot.studio.tree

import io.github.youndie.kompot.spec.KompotSpecResources
import io.github.youndie.kompot.spec.childSlots
import io.github.youndie.kompot.studio.edit.JsonEdits
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Where a drag ends, and what the body looks like afterwards.
class DragAndDropTest {
    private val slots = childSlots(KompotSpecResources("kompot-spec").schemas())

    private val body =
        """
        {
          "type": "column",
          "id": "root",
          "children": [
            { "type": "text", "id": "one", "text": "one" },
            { "type": "row", "id": "inner", "children": [
              { "type": "text", "id": "two", "text": "two" }
            ] }
          ]
        }
        """.trimIndent()

    private fun tree() = assertNotNull(screenTree(Json.parseToJsonElement(body), slots))

    private fun node(path: String) = assertNotNull(tree().flatten().firstOrNull { it.path == path }, path)

    @Test
    fun `a drop on a container goes inside it, at the end`() {
        val target = assertNotNull(dropTargetFor(node("$"), slots))

        assertEquals("$", target.parentPath)
        assertEquals("children", target.slot)
        // After the two that are there — appending is what "drop on the box" means, and inserting at
        // the front would put a new node above a title somebody wrote first.
        assertEquals(2, target.index)
        assertFalse(target.replacing)
    }

    @Test
    fun `a drop on a leaf goes next to it`() {
        val target = assertNotNull(dropTargetFor(node("$.children[0]"), slots))

        assertEquals("$", target.parentPath)
        assertEquals("children", target.slot)
        // After the node the drop landed on, not into it: a text has no room inside.
        assertEquals(1, target.index)
        assertFalse(target.replacing)
    }

    @Test
    fun `a drop into a slot with room for one is an overwrite and says so`() {
        val single =
            """
            { "type": "paginated_list", "id": "list", "initialItems": [],
              "emptyState": { "type": "text", "id": "empty", "text": "nothing" } }
            """.trimIndent()
        val root = assertNotNull(screenTree(Json.parseToJsonElement(single), slots))
        val emptyState = assertNotNull(root.flatten().firstOrNull { it.path == "$.emptyState" })

        val target = assertNotNull(dropTargetFor(emptyState, slots))

        assertEquals("emptyState", target.slot)
        // The flag the window asks a question about. Without it this drop silently eats a node.
        assertTrue(target.replacing)
    }

    @Test
    fun `nothing can be dropped on the root itself`() {
        val bare = assertNotNull(screenTree(Json.parseToJsonElement("""{ "type": "text", "id": "only" }"""), slots))

        // A text at the root has no container to be a sibling in and no slot of its own. "Nowhere" is
        // the honest answer; a made-up target would move the root somewhere it cannot go.
        assertNull(dropTargetFor(bare, slots))
    }

    @Test
    fun `a node cannot be dropped into itself or into what it contains`() {
        val intoOwnChild = assertNotNull(dropTargetFor(node("$.children[1]"), slots))

        assertFalse(canMove("$.children[1]", intoOwnChild))
        // The positive control: the same node into a different parent is allowed, so the guard is
        // refusing a cycle rather than refusing everything.
        assertTrue(canMove("$.children[0]", intoOwnChild))
    }

    @Test
    fun `adding to a slot puts the node where the target said and leaves the rest alone`() {
        val added =
            assertNotNull(
                JsonEdits.insertInto(body, "$", "children", 1, """{ "type": "text", "id": "new" }""", many = true),
            )

        val ids = Json.parseToJsonElement(added).let { screenTree(it, slots) }!!.children.map { it.id }
        assertEquals(listOf("one", "new", "inner"), ids)
        // The text around it is untouched: this is a splice, so a body somebody has formatted by hand
        // comes back formatted by hand.
        assertTrue(added.contains("\"id\": \"root\""))
        assertTrue(added.lines().size >= body.lines().size)
    }

    @Test
    fun `a slot that is not in the body yet is written as a list of one`() {
        val empty = """{ "type": "column", "id": "root" }"""

        val added = assertNotNull(JsonEdits.insertInto(empty, "$", "children", 0, """{ "type": "text", "id": "new" }""", many = true))

        // A container a server sent without its `children` is the ordinary empty container, and it has
        // to be fillable — refusing here would make the palette work only on boxes already full.
        assertEquals(listOf("new"), screenTree(Json.parseToJsonElement(added), slots)!!.children.map { it.id })
    }

    @Test
    fun `a slot with room for one is written rather than turned into a list`() {
        val single = """{ "type": "paginated_list", "id": "list", "initialItems": [], "emptyState": { "type": "text", "id": "old" } }"""

        val replaced =
            assertNotNull(
                JsonEdits.insertInto(single, "$", "emptyState", 0, """{ "type": "text", "id": "new" }""", many = false),
            )

        val root = assertNotNull(screenTree(Json.parseToJsonElement(replaced), slots))
        assertEquals(listOf("new"), root.children.map { it.id })
        // Not `[{...}]`: the schema says one, and a list here would be a body the server refuses.
        assertFalse(replaced.contains("\"emptyState\": ["))
    }

    @Test
    fun `moving a node takes its children with it`() {
        val moved = assertNotNull(JsonEdits.moveInto(body, "$.children[1]", "$", "children", 0, many = true))

        val root = assertNotNull(screenTree(Json.parseToJsonElement(moved), slots))
        assertEquals(listOf("inner", "one"), root.children.map { it.id })
        // The whole subtree travelled, not just the node that was grabbed.
        assertEquals(listOf("two"), root.children.first().children.map { it.id })
    }

    @Test
    fun `moving a node into a different container moves it out of the old one`() {
        val moved = assertNotNull(JsonEdits.moveInto(body, "$.children[0]", "$.children[1]", "children", 0, many = true))

        val root = assertNotNull(screenTree(Json.parseToJsonElement(moved), slots))
        // One child left at the top, and it is the row.
        assertEquals(listOf("inner"), root.children.map { it.id })
        // The text arrived inside it, ahead of what was already there.
        assertEquals(listOf("one", "two"), root.children.first().children.map { it.id })
    }

    @Test
    fun `a target named before the move survives the renumbering the move causes`() {
        // Taking `one` out makes `inner` the first child, so both the destination path and the
        // position asked for were measured on a body that no longer exists by the time the insertion
        // happens. Written the obvious way this silently drops the node in the wrong place — or, when
        // the destination was the node's own later sibling, nowhere at all.
        val toTheEnd = assertNotNull(JsonEdits.moveInto(body, "$.children[0]", "$", "children", 2, many = true))
        assertEquals(
            listOf("inner", "one"),
            screenTree(Json.parseToJsonElement(toTheEnd), slots)!!.children.map { it.id },
        )

        // The control for the other direction: moving the LATER node has nothing to renumber, and the
        // same code must leave that case alone.
        val toTheFront = assertNotNull(JsonEdits.moveInto(body, "$.children[1]", "$", "children", 0, many = true))
        assertEquals(
            listOf("inner", "one"),
            screenTree(Json.parseToJsonElement(toTheFront), slots)!!.children.map { it.id },
        )
    }

    @Test
    fun `a move into a node's own subtree is refused rather than performed`() {
        assertNull(JsonEdits.moveInto(body, "$.children[1]", "$.children[1]", "children", 0, many = true))
    }
}
