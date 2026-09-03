package io.github.youndie.kompot.studio.diagnostics

import io.github.youndie.kompot.studio.KompotStudioConfig
import io.github.youndie.kompot.studio.toolkitRegistry
import io.github.youndie.kompot.theme.KompotPalette
import io.github.youndie.kompot.theme.KompotTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The layer that fires on a body which is spelled correctly, valid by the schema, drawn without a word
// of complaint — and still not what anybody meant.
class VocabularyRulesTest {
    private val brandA =
        KompotTheme(
            id = "a",
            light = KompotPalette(colors = mapOf("primary" to "#FF000000", "promo_gold" to "#FFFFD700")),
            dark = KompotPalette(colors = mapOf("primary" to "#FFFFFFFF")),
        )

    private val brandB =
        KompotTheme(
            id = "b",
            light = KompotPalette(colors = mapOf("primary" to "#FF111111")),
            dark = KompotPalette(colors = mapOf("primary" to "#FF222222")),
        )

    private val config =
        KompotStudioConfig(
            registry = toolkitRegistry,
            vocabulary = mapOf("button" to mapOf("variant" to setOf("primary", "quiet"))),
            themes = mapOf("brand-a" to brandA, "brand-b" to brandB),
            brands = listOf("brand-a", "brand-b"),
        )

    @Test
    fun `a word outside the set this client knows is a warning with a path`() {
        val body =
            """{"type":"column","id":"root","children":[
                 {"type":"button","id":"b","text":"Go","action":{"type":"close"},"variant":"shouty"}
               ]}"""

        val finding = diagnose(config, body).single { it.layer == "vocabulary" }

        assertEquals("$.children[0].variant", finding.path)
        assertEquals(Severity.WARNING, finding.severity)
        assertTrue("shouty" in finding.message && "neutral" in finding.message)
    }

    @Test
    fun `a word the set contains says nothing, and so does a field nobody declared`() {
        // Two controls in one. The first stops the rule being "every string is a warning"; the second
        // is the reason the rule takes a vocabulary at all — a field whose words nobody handed over
        // cannot be checked, and guessing would report every screen.
        val known =
            """{"type":"button","id":"b","text":"Go","action":{"type":"close"},"variant":"quiet"}"""
        val undeclared = """{"type":"text","id":"t","text":"hi","ellipsis":false}"""

        assertEquals(emptyList(), diagnose(config, known).filter { it.layer == "vocabulary" })
        assertEquals(emptyList(), diagnose(config, undeclared).filter { it.layer == "vocabulary" })
    }

    @Test
    fun `a token is reported per kit and per palette that does not name it`() {
        val body = """{"type":"text","id":"t","text":"hi","color":"promo_gold"}"""

        val findings = diagnose(config, body).filter { it.layer == "vocabulary" }

        // brand-a names it in light and not in dark; brand-b names it nowhere. Three sentences, and
        // each one is a place somebody can go and fix — "the token is missing" would be one sentence
        // and no address.
        assertEquals(3, findings.size, "expected three, got ${findings.map { it.message }}")
        assertTrue(findings.any { "brand-a/dark" in it.message })
        assertTrue(findings.any { "brand-b/light" in it.message })
        assertTrue(findings.any { "brand-b/dark" in it.message })
        assertTrue(findings.all { it.path == "$.color" })
    }

    @Test
    fun `a token of the toolkit's own set is answered for and says nothing`() {
        // The control that keeps the check from firing on every screen ever written: `primary` and
        // every other Material role resolve through the built-in design system, which is an answer
        // rather than a fallback.
        val body = """{"type":"text","id":"t","text":"hi","color":"on_surface","style":"body_large"}"""

        assertEquals(emptyList(), diagnose(config, body).filter { it.layer == "vocabulary" })
    }

    @Test
    fun `without kits the token half does not run rather than reporting everything`() {
        val without = KompotStudioConfig(registry = toolkitRegistry)
        val body = """{"type":"text","id":"t","text":"hi","color":"promo_gold"}"""

        assertEquals(emptyList(), diagnose(without, body).filter { it.layer == "vocabulary" })
    }
}
