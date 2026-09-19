package io.github.youndie.kompot.studio

import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotModifierNode
import io.github.youndie.kompot.spec.KompotProtocol
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.TextComponent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

// The wire name of a sample, and the three ways a `samples` list can be wrong.
//
// Each of them used to be silent. The name was a string the deployment typed, so a typo left the
// palette showing no sample — indistinguishable from a deployment that had declared none — and the
// readme documented a `wireTypeOf` helper that was never written (B-37, B-38).
class SamplesTest {
    @Test
    fun `the wire name comes from the encoder, not from the deployment`() {
        val config =
            KompotStudioConfig(
                registry = toolkitRegistry,
                samples = listOf(TextComponent(id = "a", text = "hello"), ColumnComponent(id = "b", children = emptyList())),
            )

        // Not "the map has two entries": the keys are the assertion, and they are the strings a
        // server writes, taken from nowhere in this test.
        assertEquals(listOf("text", "column"), samplesByWireType(config).keys.toList())
    }

    @Test
    fun `declaration order survives, because the panel shows them in it`() {
        val config =
            KompotStudioConfig(
                registry = toolkitRegistry,
                samples = listOf(ColumnComponent(id = "b", children = emptyList()), TextComponent(id = "a", text = "hello")),
            )

        assertEquals(listOf("column", "text"), samplesByWireType(config).keys.toList())
    }

    @Test
    fun `a sample the Json cannot encode is named, not skipped`() {
        // A component of the deployment's own, absent from the serializers module the studio was
        // configured with. It has no wire name here and would have none on a real response either —
        // which is why this is an error and not a fallback to the class name.
        val config = KompotStudioConfig(registry = toolkitRegistry, samples = listOf(UnregisteredComponent(id = "x")))

        val failure = assertFails { samplesByWireType(config) }
        assertTrue("UnregisteredComponent" in failure.message.orEmpty(), failure.message.orEmpty())
        assertTrue("serializers module" in failure.message.orEmpty(), failure.message.orEmpty())
    }

    @Test
    fun `two samples of one type are named, rather than one winning`() {
        val config =
            KompotStudioConfig(
                registry = toolkitRegistry,
                samples = listOf(TextComponent(id = "a", text = "first"), TextComponent(id = "b", text = "second")),
            )

        val failure = assertFails { samplesByWireType(config) }
        assertTrue("\"text\"" in failure.message.orEmpty(), failure.message.orEmpty())
    }

    @Test
    fun `a sample of a type no schema declares stops the studio, naming the type`() {
        val config =
            KompotStudioConfig(
                registry = toolkitRegistry,
                schemas = mapOf(KompotProtocol.PROFILE_FILE_NAME to profileOf("column")),
                samples = listOf(TextComponent(id = "a", text = "hello")),
            )

        val failure = assertFails { checkSamples(config) }
        assertTrue("text" in failure.message.orEmpty(), failure.message.orEmpty())
    }

    @Test
    fun `a type the deployment declared as an extension is allowed through`() {
        val config =
            KompotStudioConfig(
                registry = toolkitRegistry,
                schemas = mapOf(KompotProtocol.PROFILE_FILE_NAME to profileOf("column")),
                samples = listOf(TextComponent(id = "a", text = "hello")),
                extensionTypes = setOf("text"),
            )

        checkSamples(config)
    }

    @Test
    fun `no profile means nothing to check against, not everything wrong`() {
        // The control for the check above: with the same sample and no vocabulary at all, the studio
        // opens. A guard that fired here would make an unconfigured studio unusable rather than blind,
        // and the palette is already empty in this case.
        val config =
            KompotStudioConfig(
                registry = toolkitRegistry,
                schemas = emptyMap(),
                samples = listOf(TextComponent(id = "a", text = "hello")),
            )

        checkSamples(config)
    }

    private fun assertFails(block: () -> Unit): Throwable {
        try {
            block()
        } catch (failure: Throwable) {
            return failure
        }
        fail("expected a failure and got none")
    }

    private fun profileOf(vararg wireTypes: String): JsonObject =
        Json.parseToJsonElement(
            """
            {
              "${'$'}defs": {
                "${KompotProtocol.COMPONENT_HIERARCHY}": {
                  "discriminator": {
                    "mapping": {
            ${wireTypes.joinToString(",\n") { "          \"$it\": \"kompot-standard.schema.json#/\$defs/X\"" }}
                    }
                  }
                }
              }
            }
            """.trimIndent(),
        ) as JsonObject

    @Serializable
    @SerialName("unregistered_component")
    private data class UnregisteredComponent(
        override val id: String,
        override val modifiers: List<KompotModifierNode> = emptyList(),
    ) : KompotComponent
}
