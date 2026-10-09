package io.github.youndie.kompot.commands

import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.generated.generatedStandardSerializersModule
import io.github.youndie.kompot.kompotCoreSerializersModule
import io.github.youndie.kompot.realtime.UpdateComponentMessage
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.TextComponent
import io.github.youndie.kompot.standard.column
import io.github.youndie.kompot.standard.kompotStandardSerializersModule
import io.github.youndie.kompot.standard.text
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.modules.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UpdateAndLoadTest {
    private val json =
        Json {
            classDiscriminator = "type"
            encodeDefaults = false
            serializersModule =
                kompotCoreSerializersModule + kompotStandardSerializersModule + generatedStandardSerializersModule +
                kompotCommandsSerializersModule
        }

    private fun encode(action: KompotAction): JsonObject =
        json.encodeToJsonElement(PolymorphicSerializer(KompotAction::class), action).jsonObject

    private fun decode(text: String): KompotAction =
        json.decodeFromString(PolymorphicSerializer(KompotAction::class), text)

    private fun SerialDescriptor.fieldNames(): Set<String> = (0 until elementsCount).map { getElementName(it) }.toSet()

    @Test
    fun `an update round-trips through the open KompotAction type`() {
        val update =
            UpdateAction(
                updates = listOf(UpdateComponentMessage("title", TextComponent(id = "title", text = "v2"))),
                deeplink = "app://catalog?brand=acme",
                history = UpdateHistory.REPLACE,
            )

        assertEquals(update, decode(json.encodeToString(PolymorphicSerializer(KompotAction::class), update)))
    }

    @Test
    fun `a load round-trips and carries its url`() {
        val load = LoadAction(url = "/ui/catalog/results?brand=acme")

        assertEquals("""{"type":"load","url":"/ui/catalog/results?brand=acme"}""", encode(load).toString())
        assertEquals(load, decode("""{"type":"load","url":"/ui/catalog/results?brand=acme"}"""))
    }

    // The research's own example decodes as written: the frames are the update channel's, unchanged.
    @Test
    fun `the wire form of an update decodes to the action`() {
        val decoded =
            decode(
                """{"type":"update","updates":[{"componentId":"title","component":{"type":"text","id":"title","text":"v2"}}],"deeplink":"app://catalog","history":"push"}""",
            )

        assertEquals(
            UpdateAction(
                updates = listOf(UpdateComponentMessage("title", TextComponent(id = "title", text = "v2"))),
                deeplink = "app://catalog",
                history = "push",
            ),
            decoded,
        )
    }

    @Test
    fun `history means push unless it says replace`() {
        assertEquals(UpdateHistory.PUSH, UpdateHistory.of(null))
        assertEquals(UpdateHistory.PUSH, UpdateHistory.of("push"))
        assertEquals(UpdateHistory.REPLACE, UpdateHistory.of("replace"))
        assertEquals(UpdateHistory.PUSH, UpdateHistory.of("sideways"))
    }

    // Every wire field of an update — its own and a frame's — can be set from the builder, asked of the
    // wire rather than of a list kept by hand, the way DslReachesTheWireTest asks it of the components:
    // a field the builder cannot reach stays at its default and is missing from the JSON.
    @Test
    fun `every field of an update can be set from the builder`() {
        val witness =
            encode(
                kompotUpdate(deeplink = "app://catalog?brand=acme", history = UpdateHistory.REPLACE) {
                    text("3 in the cart", id = "cart-badge")
                },
            )
        val frame = (witness.getValue("updates") as JsonArray).single().jsonObject

        assertEquals(UpdateAction.serializer().descriptor.fieldNames() + "type", witness.keys)
        assertEquals(UpdateComponentMessage.serializer().descriptor.fieldNames(), frame.keys)
    }

    // A frame names the node it carries: the builder takes the address from the node, so the two
    // cannot disagree, and the screen DSL works inside it with every id it would have on a screen.
    @Test
    fun `the builder addresses every node by its own id in the order written`() {
        val update =
            kompotUpdate {
                column(id = "results") { text("one") }
                text("3 in the cart", id = "cart-badge")
            }

        assertEquals(listOf("results", "cart-badge"), update.updates.map { it.componentId })
        assertEquals(update.updates.map { it.componentId }, update.updates.map { it.component.id })
        // Below a named node the paths are the screen's own, so a nested node keeps the id it has there.
        val results = update.updates.first().component as ColumnComponent
        assertEquals("results/0", results.children.single().id)
    }

    // An unnamed node would get an id the screen never had and be ignored on arrival (§10.2) — an update
    // that changes nothing and says nothing. The builder refuses it where it is written.
    @Test
    fun `an unnamed node in an update is refused`() {
        val failure = assertFailsWith<IllegalStateException> { kompotUpdate { text("no id") } }
        assertTrue("id" in failure.message.orEmpty(), failure.message)
    }

    @Test
    fun `a frame keeps the node's own type`() {
        val node: KompotComponent = TextComponent(id = "title", text = "v2")
        val frame = encode(UpdateAction(listOf(UpdateComponentMessage("title", node)))).getValue("updates") as JsonArray

        assertEquals(
            "text",
            frame
                .single()
                .jsonObject
                .getValue("component")
                .jsonObject
                .getValue("type")
                .jsonPrimitive.content,
        )
    }
}
