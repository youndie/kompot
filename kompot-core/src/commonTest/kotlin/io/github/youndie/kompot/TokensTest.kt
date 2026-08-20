package io.github.youndie.kompot

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

// ColorToken and TypographyToken are open string tokens (see Tokens.kt): a backend may send a key
// that is not among the companion constants, and that must not require a change in kompot-core.
// What is under test is precisely that openness, not the completeness of the constant list.
class TokensTest {
    @Test
    fun `color tokens with the same key are equal regardless of how they were constructed`() {
        assertEquals(ColorToken("primary"), ColorToken("primary"))
        assertEquals(ColorToken("primary").hashCode(), ColorToken("primary").hashCode())
    }

    @Test
    fun `color tokens with different keys are not equal`() {
        assertNotEquals(ColorToken("primary"), ColorToken("secondary"))
    }

    @Test
    fun `a color token key not present among any known constants still constructs fine`() {
        val custom = ColorToken("brand_gradient_start")

        assertEquals("brand_gradient_start", custom.key)
    }

    @Test
    fun `typography tokens follow the same open-key contract`() {
        assertEquals("headline_large", TypographyToken("headline_large").key)
    }

    @Test
    fun `tokens serialize as their bare string key — not as a wrapping object`() {
        assertEquals("\"primary\"", Json.encodeToString(ColorToken("primary")))
        assertEquals("\"headline_large\"", Json.encodeToString(TypographyToken("headline_large")))
    }

    @Test
    fun `tokens deserialize back from a bare JSON string`() {
        assertEquals(ColorToken("primary"), Json.decodeFromString<ColorToken>("\"primary\""))
        assertEquals(ColorToken("brand_custom"), Json.decodeFromString<ColorToken>("\"brand_custom\""))
    }
}
