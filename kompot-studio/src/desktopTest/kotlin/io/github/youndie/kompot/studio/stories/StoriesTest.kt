package io.github.youndie.kompot.studio.stories

import io.github.youndie.kompot.standard.TextComponent
import io.github.youndie.kompot.studio.KompotStudioConfig
import io.github.youndie.kompot.studio.toolkitRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// "Every component in every state", generated from two lists a deployment already keeps rather than
// drawn by somebody and left to age.
class StoriesTest {
    private val config =
        KompotStudioConfig(
            registry = toolkitRegistry,
            samples = listOf("text" to TextComponent(id = "sample", text = "Sample")),
            vocabulary =
                mapOf(
                    "text" to mapOf("color" to setOf("on_surface", "error")),
                    // A type whose words are known and whose sample nobody wrote.
                    "usage_counter_card" to mapOf("state" to setOf("empty", "low", "full")),
                ),
        )

    @Test
    fun `a sample becomes a body with its type on the root`() {
        val sample = storiesFor(config).first { it.name == "sample" }
        val body = Json.parseToJsonElement(assertNotNull(sample.body)) as JsonObject

        // Polymorphically encoded, which is the whole point of going through the wire: a concrete
        // serialiser writes no discriminator for the root it is handed, and a story that decoded to
        // nothing would be a picture of the bug this toolkit exists to prevent.
        assertEquals("text", (body["type"] as JsonPrimitive).content)
    }

    @Test
    fun `a word of an open field becomes a story of its own`() {
        val stories = storiesFor(config).filter { it.group == "text" && it.name.startsWith("color=") }

        assertEquals(listOf("color=error", "color=on_surface"), stories.map { it.name })

        val error = Json.parseToJsonElement(assertNotNull(stories.first().body)) as JsonObject
        // The sample edited by ONE property: rebuilding the component would mean a `when` over every
        // type a deployment has, which is the list this whole panel exists to stop keeping by hand.
        assertEquals("error", (error["color"] as JsonPrimitive).content)
        assertEquals("Sample", (error["text"] as JsonPrimitive).content)
        assertEquals("text", (error["type"] as JsonPrimitive).content)
    }

    @Test
    fun `a type with words and no sample is shown as the gap it is`() {
        val gaps = storiesFor(config).filter { it.group == "usage_counter_card" }

        assertEquals(3, gaps.size)
        // Present and empty, not absent: "which of our components has nobody ever drawn" is a question
        // this panel can answer only if the gaps are on it.
        assertTrue(gaps.all { it.body == null })
        assertEquals(listOf("state=empty", "state=full", "state=low"), gaps.map { it.name })
    }

    @Test
    fun `a configuration with neither list has no stories and no complaint`() {
        assertEquals(emptyList(), storiesFor(KompotStudioConfig(registry = toolkitRegistry)))
    }

    @Test
    fun `the screenshot fixtures are read out of the generated registry`() {
        val stories = viddikStories()

        // Against a registry standing at the name and in the shape the generator really writes, so the
        // reflection is asserted rather than hoped for. Without this the absent case would be the only
        // one covered — and the absent case passes with no implementation at all.
        assertEquals(listOf("A", "B"), stories.map { it.name })
        assertEquals(listOf("Brand", "Brand"), stories.map { it.group })
        assertEquals(100, stories.first().width)
    }

    @Test
    fun `without a generated registry there are no fixtures and no failure`() {
        val without =
            object : ClassLoader(StoriesTest::class.java.classLoader) {
                override fun loadClass(
                    name: String,
                    resolve: Boolean,
                ): Class<*> {
                    if (name.startsWith("ru.workinprogress.viddik.generated")) throw ClassNotFoundException(name)
                    return super.loadClass(name, resolve)
                }
            }

        assertEquals(emptyList(), viddikStories(without))
    }
}
