package io.github.youndie.kompot.studio.export

import io.github.youndie.kompot.KompotModifierNode
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.studio.KompotStudioConfig
import io.github.youndie.kompot.studio.SAMPLE_BODY
import io.github.youndie.kompot.studio.toolkitRegistry
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// The draft of the server side, and the two things worth asserting about a draft: that it says the
// same thing the body said, and that it compiles. The second is not a test at all — SampleScreenDraft
// is an ordinary source file in this source set, so the compiler checks it before any of this runs.
class DslExportTest {
    private val config = KompotStudioConfig(registry = toolkitRegistry)

    private fun export(
        body: String,
        function: String = "screen",
    ) = exportDsl(config, Json.parseToJsonElement(body), null, function)

    @Test
    fun `the compiled draft describes the same screen as the body it came from`() {
        val drafted = sampleScreenDraft() as ColumnComponent
        val decoded = config.json.decodeFromString<ColumnComponent>(SAMPLE_BODY)

        // Everything but the padding, which is the one place the two spellings differ.
        assertEquals(decoded.copy(modifiers = emptyList()), drafted.copy(modifiers = emptyList()))

        // `all` is a per-side FALLBACK rather than a fifth side — KompotClient reads
        // `node.start ?: node.all ?: 0` — so the four sides written out are the same padding, and the
        // DSL builder has no way to say `all`. Pinned in both spellings rather than normalised away:
        // the day `all` stops being a fallback, this is the test that has to be looked at.
        assertEquals(listOf(KompotModifierNode.Padding(all = 16)), decoded.modifiers)
        assertEquals(
            listOf(KompotModifierNode.Padding(top = 16, bottom = 16, start = 16, end = 16)),
            drafted.modifiers,
        )
    }

    @Test
    fun `the exporter still produces the draft that is checked in`() {
        // The other half of the compilation check: without it, an exporter that started emitting
        // something else would leave the old file compiling happily and prove nothing about the code
        // anybody actually runs.
        val checkedIn = Path.of(System.getProperty("draft.checkedIn")).readText()

        assertEquals(
            checkedIn,
            exportDsl(
                config,
                Json.parseToJsonElement(SAMPLE_BODY),
                "io.github.youndie.kompot.studio.export",
                "sampleScreenDraft",
            ),
        )
    }

    @Test
    fun `a consumer's own component comes out as a named guess, marked`() {
        val body =
            """
            { "type": "column", "id": "root", "children": [
              { "type": "usage_counter_card", "id": "usage", "title": "Requests", "used": 12 }
            ] }
            """.trimIndent()

        val drafted = export(body)

        // The name follows the toolkit's own convention, because that is the only rule there is — and
        // it is a rule about names, not a fact about this class, so it says so.
        assertTrue(drafted.contains("UsageCounterCardComponent("), drafted)
        assertTrue(drafted.contains("TODO: check this name"), drafted)
        // Named arguments read off the body, so the draft carries the values rather than a shape.
        assertTrue(drafted.contains("""title = "Requests""""), drafted)
        assertTrue(drafted.contains("used = 12"), drafted)
    }

    @Test
    fun `a toolkit type is not marked`() {
        // The positive control for the marker: the same guess is made for `text`, and this repository
        // compiles the result — so marking it would be crying wolf on every line.
        // The header mentions TODO by way of explaining it, so the marker itself is what is checked.
        assertFalse(export(SAMPLE_BODY).contains("TODO: check this name"), export(SAMPLE_BODY))
    }

    @Test
    fun `an action with nothing but its type comes out without parentheses`() {
        // `CloseAction` is a data object. `CloseAction()` does not compile, and a draft that does not
        // compile is the one thing this whole file exists to prevent.
        val drafted = export("""{ "type": "column", "id": "root", "children": [
            { "type": "button", "id": "b", "text": "Close", "action": { "type": "close" } }] }""")

        assertTrue(drafted.contains("button(\"Close\", CloseAction, id = \"b\")"), drafted)
        assertFalse(drafted.contains("CloseAction("), drafted)
    }

    @Test
    fun `an action outside the profile becomes a TODO rather than a wrong constructor`() {
        val drafted = export("""{ "type": "column", "id": "root", "children": [
            { "type": "button", "id": "b", "text": "Go", "action": { "type": "open_vault" } }] }""")

        // Nothing here knows what class that is, and inventing `OpenVaultAction` would compile on some
        // deployments and not others. `TODO()` returns Nothing, so it compiles everywhere and stops.
        assertTrue(drafted.contains("""TODO("open_vault")"""), drafted)
    }

    @Test
    fun `an id the DSL would have produced by itself is left out`() {
        val body =
            """
            { "type": "column", "id": "root", "children": [
              { "type": "text", "id": "root/0", "text": "unnamed" },
              { "type": "text", "id": "greeting", "text": "named" }
            ] }
            """.trimIndent()

        val drafted = export(body)

        // B-07 made the paths deterministic, so `root/0` is what the DSL produces with no id at all.
        // Printing it back would be noise that goes stale the moment somebody inserts a node above.
        assertFalse(drafted.contains("root/0"), drafted)
        assertTrue(drafted.contains("""id = "greeting""""), drafted)
    }

    @Test
    fun `a modifier the builder cannot express falls back to the exact constructor`() {
        val body =
            """
            { "type": "column", "id": "root", "children": [
              { "type": "text", "id": "t", "text": "hi",
                "modifiers": [ { "type": "background", "color": "surface", "role": "card" } ] }
            ] }
            """.trimIndent()

        val drafted = export(body)

        // `background(token)` has nowhere to put the role, and dropping it would export a screen that
        // draws differently from the one on screen. The constructor says all of it.
        assertTrue(drafted.contains("TextComponent("), drafted)
        assertTrue(drafted.contains("""role = "card""""), drafted)

        // The control: the same background WITHOUT a role stays in the DSL, so the fallback is about
        // what cannot be said rather than about backgrounds.
        val plain = export(body.replace(""", "role": "card"""", ""))
        assertTrue(plain.contains("background(ColorToken(\"surface\"))"), plain)
        assertFalse(plain.contains("TextComponent("), plain)
    }

    @Test
    fun `a file name becomes a function name Kotlin accepts`() {
        // Recordings are called `home-screen`; `fun home-screen()` is a parse error, and the draft is
        // written next to the recording under the recording's name.
        assertEquals("homeScreen", identifier("home-screen"))
        assertEquals("esimActivateScreen", identifier("esim-activate-screen"))
        assertEquals("screen2fa", identifier("2fa"))
        assertEquals("screen", identifier("---"))
        assertTrue(export("""{ "type": "column", "id": "root", "children": [] }""", "home-screen").contains("fun homeScreen()"))
    }

    @Test
    fun `a consumer's node inside a block is added, not merely built`() {
        val drafted =
            export(
                """
                { "type": "column", "id": "root", "children": [
                  { "type": "usage_counter_card", "id": "usage", "title": "Requests" }
                ] }
                """.trimIndent(),
            )

        // A bare constructor inside `kompotScreen { }` is an expression whose value is dropped: the
        // draft compiles and the node is not on the screen. Seen on a real export, where every one of
        // a deployment's own components was silently missing from the result.
        assertTrue(drafted.contains("addComponent(UsageCounterCardComponent("), drafted)
        // The marker survives outside the call, where a reader sees it and the compiler does not.
        assertTrue(drafted.contains(") /* TODO: check this name */"), drafted)
    }

    @Test
    fun `the marker is a block comment, so it cannot swallow the rest of a list`() {
        val drafted =
            export(
                """
                { "type": "column", "id": "root", "children": [
                  { "type": "paginated_list", "id": "list", "initialItems": [
                    { "type": "usage_counter_card", "id": "a", "title": "A" },
                    { "type": "usage_counter_card", "id": "b", "title": "B" }
                  ] }
                ] }
                """.trimIndent(),
            )

        // Two guessed names in one `listOf(...)`. A line comment after the first would comment out
        // `, UsageCounterCardComponent(...))` — the second item and the closing parenthesis with it.
        assertFalse(drafted.contains("// TODO"), drafted)
        assertTrue(drafted.contains("/* TODO: check this name */, UsageCounterCardComponent(id = \"b\""), drafted)
    }

    @Test
    fun `a number the schema calls a float is printed as one`() {
        val drafted =
            export(
                """
                { "type": "column", "id": "root", "children": [
                  { "type": "text", "id": "t", "text": "hi",
                    "modifiers": [ { "type": "weight", "value": 1.0 }, { "type": "background", "color": "surface", "role": "card" } ] }
                ] }
                """.trimIndent(),
            )

        // `Weight.value` is a Float, and `Weight(value = 1.0)` does not compile. The schema now says
        // `format: float` beside `number` for exactly this reason; the DSL builder path writes `1.0f`
        // by itself, so the constructor path is the one this exercises — forced by the role.
        assertTrue(drafted.contains("KompotModifierNode.Weight(value = 1.0f)"), drafted)
    }

    @Test
    fun `a body that carries no screen says so instead of printing an empty file`() {
        assertTrue(export("""{ "nothing": true }""").startsWith("// the body carries no screen"))
    }
}
