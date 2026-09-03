package io.github.youndie.kompot.studio.tree

import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.kompotJson
import io.github.youndie.kompot.spec.KompotProtocol
import io.github.youndie.kompot.spec.KompotSpecResources
import io.github.youndie.kompot.spec.childSlots
import io.github.youndie.kompot.spec.walkJsonObjects
import io.github.youndie.kompot.standard.CloseAction
import io.github.youndie.kompot.standard.button
import io.github.youndie.kompot.standard.column
import io.github.youndie.kompot.standard.kompotScreen
import io.github.youndie.kompot.standard.text
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// The tree, built from a body the toolkit's own DSL produced and encoded the way a server encodes it.
// Hand-written JSON would have been a test of the JSON somebody typed; going through the DSL and a
// polymorphic root means the input is what an endpoint really answers — including the discriminator
// on the root, whose absence is the defect the whole preview path exists to catch.
class ScreenTreeTest {
    private val schemas = KompotSpecResources("kompot-spec").schemas()
    private val slots = childSlots(schemas)

    private val body =
        kompotJson().encodeToString(
            PolymorphicSerializer(KompotComponent::class),
            kompotScreen {
                text("Catalogue", id = "title")
                column(id = "card") {
                    text("A dish", id = "dish")
                    button("Order", CloseAction, id = "order")
                }
            },
        )

    @Test
    fun `the tree holds every component of the body and nothing else`() {
        val root = screenTree(Json.parseToJsonElement(body), slots)!!
        val nodes = root.flatten()

        // Against the structural walk rather than against a list retyped here: the walk visits every
        // object, and the components are the ones carrying a type the profile knows. A tree missing a
        // slot — the emptyState case that broke every hand-kept walk — fails right here.
        val walked =
            walkJsonObjects(Json.parseToJsonElement(body))
                .map { it.value }
                .filter { (it["type"] as? JsonPrimitive)?.content in slots.keys }
                .toList()

        assertEquals(walked.size, nodes.size, "the tree has ${nodes.size} nodes for ${walked.size} components")
        assertEquals(listOf("column", "text", "column", "text", "button"), nodes.map { it.wireType })
        assertEquals(listOf("root", "title", "card", "dish", "order"), nodes.map { it.id })
    }

    @Test
    fun `a node's path is the one a schema finding carries`() {
        val nodes = screenTree(Json.parseToJsonElement(body), slots)!!.flatten()

        assertEquals(
            listOf(
                "$",
                "$.children[0]",
                "$.children[1]",
                "$.children[1].children[0]",
                "$.children[1].children[1]",
            ),
            nodes.map { it.path },
        )
    }

    @Test
    fun `a node that carries words is labelled by them, and one that does not by its id`() {
        val nodes = screenTree(Json.parseToJsonElement(body), slots)!!.flatten()

        // Any node with text, not only a `text`: a button's label is as much what somebody is looking
        // for in this panel. The rule was "is it a text node" first, and the button that came out as
        // `button#order` with its label sitting right there in the object is what corrected it.
        assertEquals("""text "Catalogue"""", nodes[1].label)
        assertEquals("""button "Order"""", nodes[4].label)
        // The other half: a container carries no words, so it falls back to the id.
        assertEquals("column#card", nodes[2].label)
        assertEquals("column#root", nodes[0].label)
    }

    @Test
    fun `a type outside the profile is a node, and says it is not one of ours`() {
        val deployment =
            """
            {"type":"column","id":"root","children":[
              {"type":"esim_transfer_widget","id":"widget","label":"Transfer"},
              {"type":"text","id":"after","text":"still here"}
            ]}
            """.trimIndent()

        val nodes = screenTree(Json.parseToJsonElement(deployment), slots)!!.flatten()

        // The node has to be THERE — losing it is what building the tree from decoded components
        // would do, since an unknown type decodes to UnknownComponent and the original name is gone
        // from the object.
        assertEquals(listOf("column", "esim_transfer_widget", "text"), nodes.map { it.wireType })
        assertTrue(nodes[0].known)
        assertFalse(nodes[1].known, "a type the profile does not carry was reported as known")
        assertTrue(nodes[2].known)

        // And the sibling after it is still walked: a tree that stopped at the first unfamiliar node
        // would lose the rest of the screen.
        assertEquals("after", nodes[2].id)
    }

    @Test
    fun `an envelope's screen is the root, not the envelope`() {
        val envelope = """{"screen":{"type":"text","id":"only","text":"hi"},"realtimeTopic":"t"}"""
        val root = screenTree(Json.parseToJsonElement(envelope), slots)!!

        // The same rule KompotPreview decodes by. A tree that found its root elsewhere than the render
        // did would be a picture of a different screen.
        assertEquals("$.screen", root.path)
        assertEquals("text", root.wireType)
    }

    @Test
    fun `the profile the tree is built against is the toolkit's own`() {
        // A control on the fixture rather than on the code: if KompotSpecResources ever came back
        // empty, every assertion above would still pass with every node marked unknown.
        assertTrue(KompotProtocol.PROFILE_FILE_NAME in schemas)
        assertTrue(slots.size >= 15, "only ${slots.size} component types were read from the classpath")
        assertTrue("column" in slots && "paginated_list" in slots)
    }
}
