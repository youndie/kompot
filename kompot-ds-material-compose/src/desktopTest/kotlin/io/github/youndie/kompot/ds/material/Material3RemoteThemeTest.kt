package io.github.youndie.kompot.ds.material

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import io.github.youndie.kompot.material3.M3Colors
import io.github.youndie.kompot.theme.kompotTheme
import kotlin.test.Test
import kotlin.test.assertEquals

class Material3RemoteThemeTest {
    private val base = lightColorScheme()

    @Test
    fun `a slot the theme describes is replaced`() {
        val theme = kompotTheme("gold") { light { color(M3Colors.Primary, "#B8860B") } }

        val scheme = theme.toMaterialColorScheme(base, darkMode = false)

        assertEquals(Color(0xFFB8860B), scheme.primary)
    }

    // An overlay, not a replacement: a brand that describes one token does not blank out the rest.
    @Test
    fun `every slot the theme leaves out keeps the base value`() {
        val theme = kompotTheme("gold") { light { color(M3Colors.Primary, "#B8860B") } }

        val scheme = theme.toMaterialColorScheme(base, darkMode = false)

        assertEquals(base.surface, scheme.surface)
        assertEquals(base.onSurface, scheme.onSurface)
        assertEquals(base.error, scheme.error)
        assertEquals(base.outline, scheme.outline)
    }

    @Test
    fun `dark mode reads the dark palette`() {
        val theme =
            kompotTheme("gold") {
                light { color(M3Colors.Primary, "#B8860B") }
                dark { color(M3Colors.Primary, "#FFD770") }
            }

        assertEquals(Color(0xFFFFD770), theme.toMaterialColorScheme(base, darkMode = true).primary)
    }

    // The same boundary as RemoteThemeDesignSystem: with no dark palette, dark mode stays entirely on
    // the built-in scheme instead of mixing in light brand colours.
    @Test
    fun `a theme with no dark palette leaves the base scheme untouched in dark mode`() {
        val theme = kompotTheme("light-only") { light { color(M3Colors.Primary, "#B8860B") } }

        assertEquals(base.primary, theme.toMaterialColorScheme(base, darkMode = true).primary)
    }

    @Test
    fun `a malformed hex value leaves that slot on the base scheme`() {
        val theme = kompotTheme("broken") { light { color(M3Colors.Primary, "rebeccapurple") } }

        assertEquals(base.primary, theme.toMaterialColorScheme(base, darkMode = false).primary)
    }

    // The scheme is all a Material component sees, so the mapping has to cover the whole token set
    // rather than the handful a demo theme happens to describe.
    @Test
    fun `every M3 color token maps to a scheme slot`() {
        val theme = kompotTheme("all") { light { M3Colors.all.forEach { color(it, "#010203") } } }

        val scheme = theme.toMaterialColorScheme(base, darkMode = false)
        val expected = Color(0xFF010203)

        listOf(
            scheme.primary, scheme.onPrimary, scheme.primaryContainer, scheme.onPrimaryContainer,
            scheme.secondary, scheme.onSecondary, scheme.secondaryContainer, scheme.onSecondaryContainer,
            scheme.surface, scheme.onSurface, scheme.surfaceVariant, scheme.onSurfaceVariant,
            scheme.background, scheme.onBackground,
            scheme.error, scheme.onError, scheme.errorContainer, scheme.onErrorContainer,
            scheme.outline, scheme.outlineVariant,
        ).forEachIndexed { index, slot ->
            assertEquals(expected, slot, "slot #$index did not pick the value up from the theme")
        }
    }
}
