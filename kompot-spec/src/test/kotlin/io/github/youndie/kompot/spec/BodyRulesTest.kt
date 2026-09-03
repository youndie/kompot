package io.github.youndie.kompot.spec

import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.standard.column
import io.github.youndie.kompot.standard.kompotScreen
import io.github.youndie.kompot.standard.row
import io.github.youndie.kompot.standard.text
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.overwriteWith
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The rules a schema cannot express, each with the control that stops it from passing for the wrong
// reason. Every one of these claims is about a WHOLE body — two ids in different subtrees, a string
// kept in two places, a schema and a screen naming each other — which is exactly why none of them
// could be a schema keyword.
class BodyRulesTest {
    private val documents: Map<String, JsonObject> =
        KompotSpec.generateAll(KompotToolkitSpec.modules).let { schemas ->
            schemas.associate { it.fileName to it.document } +
                (KompotProtocol.PROFILE_FILE_NAME to KompotSpec.profile(schemas))
        }

    private val componentTypes = childSlots(documents).keys

    private fun parse(body: String) = Json.parseToJsonElement(body)

    @Test
    fun `an id that repeats is reported at the node that repeats it`() {
        val body =
            """
            {"type":"column","id":"root","children":[
              {"type":"text","id":"twice","text":"a"},
              {"type":"text","id":"twice","text":"b"}
            ]}
            """.trimIndent()

        val findings = BodyRules.componentIds(parse(body), componentTypes)

        // At the SECOND occurrence: a studio highlights the node a finding points at, and "the id
        // twice" is not a node.
        assertEquals(1, findings.size, "expected one finding, got ${findings.map { it.toString() }}")
        assertEquals("$.children[1]", findings.single().path.toString())
        assertTrue("twice" in findings.single().message)
    }

    @Test
    fun `an empty id is reported and a well-formed tree is not`() {
        val empty = """{"type":"column","id":"root","children":[{"type":"text","id":"","text":"a"}]}"""
        val clean = """{"type":"column","id":"root","children":[{"type":"text","id":"t","text":"a"}]}"""

        assertEquals(1, BodyRules.componentIds(parse(empty), componentTypes).size)
        // The control. Without it the rule is satisfied by an implementation that reports every node.
        assertEquals(emptyList(), BodyRules.componentIds(parse(clean), componentTypes))
    }

    @Test
    fun `a text and its spans have to spell the same sentence`() {
        val drifted =
            """{"type":"text","id":"t","text":"Hello world","spans":[{"text":"Hello "},{"text":"there"}]}"""
        val agreeing =
            """{"type":"text","id":"t","text":"Hello there","spans":[{"text":"Hello "},{"text":"there"}]}"""
        val noSpans = """{"type":"text","id":"t","text":"Hello world"}"""

        val findings = BodyRules.textSpans(parse(drifted))
        assertEquals(1, findings.size)
        assertEquals("$", findings.single().path.toString())
        assertTrue("Hello world" in findings.single().message && "Hello there" in findings.single().message)

        // Two controls, and both matter: a text whose spans agree, and a text with no spans at all.
        // The second is the common case, and a rule that reported it would fire on every screen.
        assertEquals(emptyList(), BodyRules.textSpans(parse(agreeing)))
        assertEquals(emptyList(), BodyRules.textSpans(parse(noSpans)))
    }

    @Test
    fun `a form's schema and its screen have to name the same fields`() {
        val body =
            """
            {"schema":{"formId":"f","fields":[
                {"type":"text_field","fieldId":"declared","rules":[]},
                {"type":"text_field","fieldId":"never_rendered","rules":[]}
              ]},
             "screen":{"type":"column","id":"root","children":[
                {"type":"text_input","id":"i","fieldId":"declared"},
                {"type":"text_input","id":"j","fieldId":"invented"}
              ]}}
            """.trimIndent()

        val findings = BodyRules.formFields(parse(body))

        val messages = findings.map { it.message }
        assertTrue(messages.any { "invented" in it }, "an undeclared field went unreported: $messages")
        assertTrue(messages.any { "never_rendered" in it }, "an unrendered field went unreported: $messages")

        // The path reaches THROUGH the envelope: a rule that walked only the screen would report
        // `$.children[1]` for a node that lives at `$.screen.children[1]`, and every click would land
        // on the wrong row.
        val undeclared = findings.first { "invented" in it.message }
        assertEquals("$.screen.children[1]", undeclared.path.toString())
    }

    @Test
    fun `a body that is not a form response is left alone by the form rule`() {
        // The control that keeps the three rules runnable over the same body: a form check that
        // complained about a screen would make the other two unusable in a studio.
        val screen = """{"type":"column","id":"root","children":[]}"""
        assertEquals(emptyList(), BodyRules.formFields(parse(screen)))
    }

    @Test
    fun `a tree the DSL built without a single explicit id satisfies the id rule`() {
        // The two halves of B-07 meeting: the DSL names an unnamed node by its path, and the rule that
        // an id must be non-empty and unique in the tree is what that naming has to keep true. A
        // counter reset per container, or a path that forgot its depth, would collide here.
        val screen =
            kompotScreen {
                text("first")
                column {
                    text("nested")
                    row { text("deeper") }
                }
                column {
                    text("nested")
                    row { text("deeper") }
                }
            }

        // The engine's own Json, assembled from the spec modules this test already has: kompotJson()
        // lives in :kompot-client, and a schema module has no business depending on a Compose client.
        val json =
            Json {
                serializersModule =
                    KompotToolkitSpec.modules.fold(EmptySerializersModule()) { all: SerializersModule, module ->
                        all.overwriteWith(module.serializersModule)
                    }
            }
        val body = json.encodeToString(PolymorphicSerializer(KompotComponent::class), screen)

        assertEquals(emptyList(), BodyRules.componentIds(parse(body), componentTypes))
    }

    @Test
    fun `check runs all three over one body`() {
        val body =
            """
            {"type":"column","id":"root","children":[
              {"type":"text","id":"twice","text":"a","spans":[{"text":"b"}]},
              {"type":"text","id":"twice","text":"c"}
            ]}
            """.trimIndent()

        val rules = BodyRules.check(parse(body), componentTypes).map { it.rule }.toSet()
        assertEquals(setOf("component-id", "text-spans"), rules)
    }
}
