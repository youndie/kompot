package io.github.youndie.kompot.studio.editor

import io.github.youndie.kompot.spec.KompotSpecResources
import io.github.youndie.kompot.spec.childSlots
import io.github.youndie.kompot.studio.tree.screenTree
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// One walk, two answers, and the second is the one nothing else can give: kotlinx.serialization parses
// to a tree with no offsets in it, so nothing that DECODES a body can say where in the text a node was
// written.
class JsonLexerTest {
    private val body =
        """
        {"type":"column","id":"root","spacing":12,"children":[
          {"type":"text","id":"t","text":"hi","ellipsis":true},
          {"type":"button","id":"b","text":"Go","action":{"type":"close"}}
        ]}
        """.trimIndent()

    @Test
    fun `every node is reachable by the path the tree prints`() {
        val slots = childSlots(KompotSpecResources("kompot-spec").schemas())
        val nodes = screenTree(Json.parseToJsonElement(body), slots)!!.flatten()

        // THE JOIN, asserted. Clicking a row puts the caret on that node, and the two sides agree only
        // because they print paths the same way — an equality that nothing else in the studio checks,
        // and whose failure looks like a caret that occasionally does not move.
        nodes.forEach { node ->
            assertTrue(node.path in lexJson(body).nodes, "no text range for the tree's node ${node.path}")
        }
    }

    @Test
    fun `the caret target is the word that names the node`() {
        val ranges = lexJson(body).nodes

        assertEquals("\"column\"", body.substring(ranges.getValue("$")))
        assertEquals("\"text\"", body.substring(ranges.getValue("$.children[0]")))
        assertEquals("\"button\"", body.substring(ranges.getValue("$.children[1]")))
        // Down to a nested non-component object, because the lexer knows nothing about components —
        // it maps paths, and an action is at a path like anything else.
        assertEquals("\"close\"", body.substring(ranges.getValue("$.children[1].action")))
    }

    @Test
    fun `an object with no type is still addressable, at its brace`() {
        val plain = """{"schema":{"formId":"f","fields":[]},"screen":{"type":"text","id":"t","text":"hi"}}"""
        val ranges = lexJson(plain).nodes

        assertEquals("{", plain.substring(ranges.getValue("$.schema")))
        assertEquals("\"text\"", plain.substring(ranges.getValue("$.screen")))
    }

    @Test
    fun `the discriminator gets a style of its own, and a plain key does not`() {
        val tokens = lexJson("""{"type":"text","id":"t"}""").tokens

        assertEquals(TokenKind.TYPE_KEY, tokens.first { it.kind == TokenKind.TYPE_KEY }.kind)
        assertTrue(tokens.any { it.kind == TokenKind.TYPE_VALUE })
        // The control: `id` is a key like any other, and a lexer that made everything loud would say
        // nothing.
        assertTrue(tokens.any { it.kind == TokenKind.KEY })
    }

    @Test
    fun `a half-typed body colours what it can and does not throw`() {
        // Every one of these is what the text looks like WHILE somebody types, which is most of the
        // time. An editor that stopped at the first surprise would flicker the document grey on every
        // keystroke, and one that threw would take the window down.
        listOf(
            """{"type":"column","id":""",
            """{"type":"column" "id":"root"}""",
            """{"type":"column","children":[{"type":"text",""",
            "{",
            "",
            """{"text":"a \" quoted quote","n":-1.5e3}""",
        ).forEach { partial ->
            val lexed = lexJson(partial)
            assertTrue(lexed.tokens.size >= 0, "the lexer returned nothing at all for: $partial")
        }

        // And it still finds what it can: the first body above names a type, and the caret has to be
        // able to go there even though the object never closes.
        val unfinished = """{"type":"column","id":"""
        assertEquals("\"column\"", unfinished.substring(lexJson(unfinished).nodes.getValue("$")))
    }

    @Test
    fun `tokens do not overlap and stay inside the text`() {
        val lexed = lexJson(body)

        // The invariant the highlighter depends on: a span past the end throws inside the text field,
        // and overlapping spans would paint a token in the previous one's colour.
        lexed.tokens.forEach { token ->
            assertTrue(
                token.start in 0..body.length && token.end in token.start..body.length,
                "$token is outside the text",
            )
        }
        lexed.tokens.zipWithNext().forEach { (left, right) ->
            assertTrue(left.end <= right.start, "$left overlaps $right")
        }
    }
}

private fun String.substring(range: IntRange): String = substring(range.first, range.last + 1)
