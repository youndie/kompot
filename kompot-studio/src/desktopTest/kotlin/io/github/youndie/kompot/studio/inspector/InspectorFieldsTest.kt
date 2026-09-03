package io.github.youndie.kompot.studio.inspector

import io.github.youndie.kompot.spec.KompotSpecResources
import io.github.youndie.kompot.studio.edit.JsonEdits
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The panel that replaces the text for somebody who is not a backend developer, and the schema is
// where every one of its answers comes from.
class InspectorFieldsTest {
    private val schemas = KompotSpecResources("kompot-spec").schemas()

    private fun fieldsOf(
        wireType: String,
        words: Map<String, Set<String>> = emptyMap(),
        tokens: Map<String, List<String>> = emptyMap(),
    ) = fieldsFor(schemas, assertNotNull(defKeyFor(schemas, wireType)), words, tokens)

    @Test
    fun `a property gets the editor its schema type calls for`() {
        val byName = fieldsOf("text").associateBy { it.name }

        assertEquals(FieldKind.STRING, byName.getValue("text").kind)
        // Nullable integer, printed as ["integer", "null"] — reading only the first form would call
        // half the properties in this toolkit untyped.
        assertEquals(FieldKind.NUMBER, byName.getValue("maxLines").kind)
        assertEquals(FieldKind.BOOLEAN, byName.getValue("ellipsis").kind)
        // The discriminator is not a field: changing it makes the node a different type, which is the
        // tree's business rather than a text box's.
        assertTrue("type" !in byName)
    }

    @Test
    fun `the description a KDoc put in the schema reaches the panel`() {
        val maxLines = fieldsOf("text").single { it.name == "maxLines" }

        // The other half of B-06 arriving where it was for: a field with a sentence shows the
        // sentence, and one without shows its name and type.
        assertEquals(
            "How many lines the text may occupy before it is cut. Null lets it take as many as it needs.",
            maxLines.description,
        )
        assertNull(fieldsOf("table").single { it.name == "rows" }.description)
    }

    @Test
    fun `a token field offers the kit's keys and a word field the deployment's words`() {
        val fields =
            fieldsOf(
                "text",
                words = mapOf("ellipsis" to setOf("never", "always")),
                tokens = mapOf("ColorToken" to listOf("primary", "on_surface")),
            ).associateBy { it.name }

        assertEquals(FieldKind.CHOICE, fields.getValue("color").kind)
        assertEquals(listOf("primary", "on_surface"), fields.getValue("color").options)

        // A declared vocabulary beats what the schema could say: the schema calls the property what
        // its Kotlin type is because the protocol leaves the value open, and the set is the only thing
        // that knows better.
        assertEquals(FieldKind.CHOICE, fields.getValue("ellipsis").kind)
        assertEquals(listOf("always", "never"), fields.getValue("ellipsis").options)
    }

    @Test
    fun `an action is a nested value naming the hierarchy its editor should offer`() {
        val action = fieldsOf("button").single { it.name == "action" }

        assertEquals(FieldKind.NESTED, action.kind)
        assertEquals("KompotAction", action.hierarchy)

        // And one level down is the same function, which is the whole reason a sub-form costs nothing:
        // an action's properties are read exactly as a component's are.
        val navigate = fieldsFor(schemas, assertNotNull(defKeyFor(schemas, "navigate", "KompotAction")))
        val deeplink = navigate.single { it.name == "deeplink" }
        assertEquals(FieldKind.STRING, deeplink.kind)
        assertNotNull(deeplink.pattern, "the deeplink's format is in the schema and the editor should hold to it")
    }

    @Test
    fun `writing a property keeps the rest of the document byte for byte`() {
        val body =
            """
            {
              "type": "column",
              "id": "root",
              "spacing": 8,
              "children": []
            }
            """.trimIndent()

        val edited = assertNotNull(JsonEdits.setProperty(body, "$", "spacing", "16"))
        val parsed = Json.parseToJsonElement(edited) as JsonObject

        assertEquals("16", (parsed["spacing"] as JsonPrimitive).content)
        assertEquals(body.lines().size, edited.lines().size)
        assertTrue(edited.contains("""  "id": "root","""), "the document was reformatted around the edit")
    }

    @Test
    fun `a property that was not there is added, and one that was can be cleared`() {
        val body = """{"type":"text","id":"t","text":"hi"}"""

        val added = assertNotNull(JsonEdits.setProperty(body, "$", "maxLines", "2"))
        assertEquals("2", ((Json.parseToJsonElement(added) as JsonObject)["maxLines"] as JsonPrimitive).content)

        val cleared = assertNotNull(JsonEdits.removeProperty(added, "$", "maxLines"))
        assertTrue("maxLines" !in cleared)
        // Still parses, which is the whole risk of a splice: a comma left behind or taken twice.
        assertEquals("hi", ((Json.parseToJsonElement(cleared) as JsonObject)["text"] as JsonPrimitive).content)
    }

    @Test
    fun `a type this build does not know has no fields, and its keys are not lost`() {
        // The panel cannot offer editors for a type it has no schema for — but the node keeps every
        // key it arrived with, because the text is the source of truth and nothing here rewrites it.
        assertNull(defKeyFor(schemas, "esim_transfer_widget"))
        assertEquals(emptyList(), fieldsFor(schemas, "NoSuchDefinition"))
    }
}
