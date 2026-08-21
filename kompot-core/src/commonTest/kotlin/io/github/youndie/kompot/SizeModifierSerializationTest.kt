package io.github.youndie.kompot

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

private val json =
    Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        serializersModule = kompotCoreSerializersModule
    }

// widthDp/heightDp were added to an existing node rather than introduced as a node of their own,
// because SPEC.md §15 allows the first and forbids the second. These tests hold both halves of that
// claim to the wire: a payload written before the fields still decodes, and a payload carrying them
// still decodes for a reader that does not know them.
class SizeModifierSerializationTest {
    @Test
    fun `a payload written before the dp fields still decodes`() {
        val node = json.decodeFromString(KompotModifierNode.serializer(), """{"type":"size","width":"Fill"}""")

        assertEquals(KompotModifierNode.Size(width = SizeType.Fill), node)
    }

    @Test
    fun `the dp fields stay out of the payload when unset`() {
        val encoded =
            json.encodeToString(
                KompotModifierNode.serializer(),
                KompotModifierNode.Size(width = SizeType.Fill),
            )

        assertEquals("""{"type":"size","width":"Fill"}""", encoded)
    }

    @Test
    fun `a reader that does not know the dp fields still reads the node`() {
        // What an older client does with a newer payload: unknown keys are dropped rather than
        // failing the whole response, which is the property §15 leans on.
        val node =
            json.decodeFromString(
                KompotModifierNode.serializer(),
                """{"type":"size","width":"Fill","depthDp":4}""",
            )

        assertEquals(KompotModifierNode.Size(width = SizeType.Fill), node)
    }

    @Test
    fun `numbers travel as numbers on both axes`() {
        val node = KompotModifierNode.Size(width = SizeType.Wrap, height = SizeType.Wrap, widthDp = 120, heightDp = 1)
        val encoded = json.encodeToString(KompotModifierNode.serializer(), node)

        assertEquals("""{"type":"size","width":"Wrap","height":"Wrap","widthDp":120,"heightDp":1}""", encoded)
        assertEquals(node, json.decodeFromString(KompotModifierNode.serializer(), encoded))
    }
}
