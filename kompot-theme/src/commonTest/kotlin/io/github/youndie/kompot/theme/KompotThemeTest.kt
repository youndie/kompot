package io.github.youndie.kompot.theme

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import io.github.youndie.kompot.ColorToken
import io.github.youndie.kompot.TypographyToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val json = Json { classDiscriminator = "type" }

private val Primary = ColorToken("primary")
private val Surface = ColorToken("surface")
private val TitleLarge = TypographyToken("title_large")

class KompotThemeTest {
    @Test
    fun `theme round-trips through JSON with both palettes and typography`() {
        val theme =
            kompotTheme("gold") {
                light { color(Primary, "#B8860B") }
                dark { color(Primary, "#FFD770") }
                typography { style(TitleLarge, fontSizeSp = 26f, fontWeight = 700) }
            }

        assertEquals(theme, json.decodeFromString<KompotTheme>(json.encodeToString(theme)))
    }

    @Test
    fun `a theme without a dark palette round-trips with dark still absent`() {
        val theme = kompotTheme("light-only") { light { color(Primary, "#B8860B") } }

        val decoded = json.decodeFromString<KompotTheme>(json.encodeToString(theme))

        assertNull(decoded.dark)
        assertEquals(theme, decoded)
    }

    @Test
    fun `colorFor reads the palette matching the requested mode`() {
        val theme =
            kompotTheme("gold") {
                light { color(Primary, "#B8860B") }
                dark { color(Primary, "#FFD770") }
            }

        assertEquals(0xFFB8860B.toInt(), theme.colorFor(Primary, darkMode = false))
        assertEquals(0xFFFFD770.toInt(), theme.colorFor(Primary, darkMode = true))
    }

    // The key degradation property: a theme overrides only what it actually described, and for
    // everything else the client stays on its own built-in design system.
    @Test
    fun `colorFor returns null for a token the palette does not override`() {
        val theme = kompotTheme("gold") { light { color(Primary, "#B8860B") } }

        assertNull(theme.colorFor(Surface, darkMode = false))
    }

    // A light palette is NOT substituted into dark mode: a brand's near-white background would
    // otherwise land under the light text of the built-in dark theme.
    @Test
    fun `colorFor returns null in dark mode when the theme has no dark palette`() {
        val theme = kompotTheme("light-only") { light { color(Primary, "#B8860B") } }

        assertNull(theme.colorFor(Primary, darkMode = true))
    }

    @Test
    fun `styleFor returns only the properties the theme actually set`() {
        val theme = kompotTheme("gold") { typography { style(TitleLarge, fontSizeSp = 26f) } }

        val style = theme.styleFor(TitleLarge)

        assertEquals(26f, style?.fontSizeSp)
        assertNull(style?.fontWeight)
        assertNull(style?.lineHeightSp)
        assertNull(style?.letterSpacingSp)
    }

    @Test
    fun `styleFor returns null for a token the theme does not describe`() {
        assertNull(kompotTheme("empty") {}.styleFor(TitleLarge))
    }
}

class ParseArgbHexTest {
    @Test
    fun `six-digit hex gets an opaque alpha`() {
        assertEquals(0xFFB8860B.toInt(), parseArgbHex("#B8860B"))
    }

    @Test
    fun `eight-digit hex keeps the alpha it was given`() {
        assertEquals(0x80B8860B.toInt(), parseArgbHex("#80B8860B"))
    }

    @Test
    fun `three-digit shorthand expands each digit`() {
        assertEquals(0xFFAABBCC.toInt(), parseArgbHex("#ABC"))
    }

    @Test
    fun `the leading hash is optional and the case does not matter`() {
        assertEquals(parseArgbHex("#b8860b"), parseArgbHex("B8860B"))
    }

    @Test
    fun `surrounding whitespace is tolerated`() {
        assertEquals(0xFFB8860B.toInt(), parseArgbHex("  #B8860B  "))
    }

    @Test
    fun `a wrong length is rejected`() {
        assertNull(parseArgbHex("#B8860"))
        assertNull(parseArgbHex("#B8860B0"))
        assertNull(parseArgbHex("#"))
        assertNull(parseArgbHex(""))
    }

    @Test
    fun `non-hex characters are rejected`() {
        assertNull(parseArgbHex("#GGGGGG"))
        assertNull(parseArgbHex("rebeccapurple"))
    }
}
