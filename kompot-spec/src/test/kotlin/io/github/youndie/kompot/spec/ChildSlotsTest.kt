package io.github.youndie.kompot.spec

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The two halves of "walk a body without keeping a list by hand": which properties hold components,
// and getting to every object with the path it was found at.
//
// Against what the GENERATOR prints rather than against the committed schema files, like every other
// test here: a list derived from the goldens would keep agreeing with them after the types moved.
class ChildSlotsTest {
    private val documents: Map<String, JsonObject> =
        KompotSpec.generateAll(KompotToolkitSpec.modules).let { schemas ->
            schemas.associate { it.fileName to it.document } +
                (KompotProtocol.PROFILE_FILE_NAME to KompotSpec.profile(schemas))
        }

    private val slots = childSlots(documents)

    @Test
    fun `a slot is a property whose ref leads back into the component hierarchy`() {
        assertEquals(listOf(Slot("children", many = true, required = true)), slots.getValue("column"))
        assertEquals(listOf(Slot("children", many = true, required = true)), slots.getValue("row"))
        assertEquals(listOf(Slot("content", many = false, required = true)), slots.getValue("wizard_screen"))

        // The one the hand-written walks kept missing, and the reason this function exists: a list
        // whose only screen is its empty state was invisible to every copy that knew about
        // initialItems alone.
        assertEquals(
            listOf(
                Slot("initialItems", many = true, required = true),
                Slot("emptyState", many = false, required = false),
            ),
            slots.getValue("paginated_list"),
        )
    }

    @Test
    fun `a property that holds something other than components is not a slot`() {
        // The negative half, without which the assertions above are satisfied by "every array is a
        // slot". `text` has spans and modifiers; `table` has rows of strings; a form field has
        // options. None of them is a place a component can go.
        assertEquals(emptyList(), slots.getValue("text"))
        assertEquals(emptyList(), slots.getValue("table"))
        assertEquals(emptyList(), slots.getValue("button"))
        assertEquals(emptyList(), slots.getValue("image"))
    }

    @Test
    fun `every type the profile can receive is answered for`() {
        val profile = documents.getValue(KompotProtocol.PROFILE_FILE_NAME)
        val mapping =
            profile
                .let { it["\$defs"] as JsonObject }
                .let { it.getValue(KompotProtocol.COMPONENT_HIERARCHY) as JsonObject }
                .let { it.getValue("discriminator") as JsonObject }
                .let { it.getValue("mapping") as JsonObject }

        // A map that answered for some types and silently skipped others would leave a caller writing
        // `slots[type].orEmpty()` and never learning which of the two an empty list meant.
        assertEquals(mapping.keys, slots.keys)
        assertTrue(slots.size >= 15, "the toolkit profile has shrunk to ${slots.size} component types")
    }

    @Test
    fun `walking with paths reaches exactly the objects the plain walk reaches`() {
        val corpus = File("../kompot-client-tck/corpus").listFiles { file -> file.name.endsWith(".json") }

        // Loudly, and not `?: return`: a corpus that moved would turn this into a test of nothing that
        // goes on passing.
        assertTrue(!corpus.isNullOrEmpty(), "no corpus cases found next door — this test would prove nothing")

        corpus.forEach { file ->
            val element = Json.parseToJsonElement(file.readText())
            val plain = collectJsonObjects(element)
            val withPaths = walkJsonObjects(element).toList()

            assertEquals(plain.size, withPaths.size, "${file.name}: the path-carrying walk lost objects")
            assertEquals(
                plain.toSet(),
                withPaths.map { it.value }.toSet(),
                "${file.name}: it reached different objects",
            )
            assertEquals(
                withPaths.size,
                withPaths.map { it.path.toString() }.toSet().size,
                "${file.name}: two objects were given the same path",
            )
        }
    }

    @Test
    fun `a path is the one a finding already carries`() {
        val body =
            """
            {"screen":{"type":"column","id":"root","children":[
              {"type":"text","id":"t","text":"hi"},
              {"type":"paginated_list","id":"l","initialItems":[],"emptyState":{"type":"text","id":"e","text":"none"}}
            ]}}
            """.trimIndent()

        val paths = walkJsonObjects(Json.parseToJsonElement(body)).map { it.path.toString() }.toList()

        // The validator's own notation, so a finding and a node line up without translating either.
        assertEquals(
            listOf(
                "$",
                "$.screen",
                "$.screen.children[0]",
                "$.screen.children[1]",
                "$.screen.children[1].emptyState",
            ),
            paths,
        )
    }
}
