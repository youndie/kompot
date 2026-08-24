package io.github.youndie.kompot.theme.client

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.youndie.kompot.KompotDesignSystem
import io.github.youndie.kompot.ColorToken
import io.github.youndie.kompot.TypographyToken
import io.github.youndie.kompot.material3.M3Colors
import io.github.youndie.kompot.material3.M3Typography
import io.github.youndie.kompot.theme.kompotTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

// A stand-in design system rather than the Material3 one, so that the test checks the overlay itself
// instead of the values of a particular palette — those are covered elsewhere.
private val FallbackColor = Color(0xFF00FF00)
private val FallbackStyle =
    TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight(400), letterSpacing = 0.5.sp)

private class FakeDesignSystem : KompotDesignSystem {
    val requestedColors = mutableListOf<ColorToken>()
    val requestedStyles = mutableListOf<TypographyToken>()

    @Composable
    override fun resolveColor(token: ColorToken): Color {
        requestedColors += token
        return FallbackColor
    }

    @Composable
    override fun resolveTypography(token: TypographyToken): TextStyle {
        requestedStyles += token
        return FallbackStyle
    }
}

@OptIn(ExperimentalTestApi::class)
class RemoteThemeDesignSystemTest {
    @Test
    fun `a token the theme describes is resolved from the theme, not the fallback`() =
        runDesktopComposeUiTest {
            val fallback = FakeDesignSystem()
            val designSystem =
                RemoteThemeDesignSystem(
                    theme = kompotTheme("gold") { light { color(M3Colors.Primary, "#B8860B") } },
                    fallback = fallback,
                    darkModeOverride = false,
                )
            var resolved: Color? = null

            setContent { resolved = designSystem.resolveColor(M3Colors.Primary) }

            waitForIdle()
            assertEquals(Color(0xFFB8860B), resolved)
            assertTrue(fallback.requestedColors.isEmpty(), "the fallback must not be asked for a token the theme describes")
        }

    // The property that matters: a theme is LAID OVER the built-in design system rather than
    // replacing it. A brand that describes three tokens out of twenty is a valid brand.
    @Test
    fun `a token the theme leaves out falls through to the fallback`() =
        runDesktopComposeUiTest {
            val fallback = FakeDesignSystem()
            val designSystem =
                RemoteThemeDesignSystem(
                    theme = kompotTheme("gold") { light { color(M3Colors.Primary, "#B8860B") } },
                    fallback = fallback,
                    darkModeOverride = false,
                )
            var resolved: Color? = null

            setContent { resolved = designSystem.resolveColor(M3Colors.Surface) }

            waitForIdle()
            assertEquals(FallbackColor, resolved)
            assertEquals(listOf(M3Colors.Surface), fallback.requestedColors)
        }

    // A typo in one colour must not drag the rest of the theme down with it — the same degradation
    // principle as UnknownComponent in :kompot-core.
    @Test
    fun `a malformed hex value degrades to the fallback instead of throwing`() =
        runDesktopComposeUiTest {
            val fallback = FakeDesignSystem()
            val designSystem =
                RemoteThemeDesignSystem(
                    theme = kompotTheme("broken") { light { color(M3Colors.Primary, "rebeccapurple") } },
                    fallback = fallback,
                    darkModeOverride = false,
                )
            var resolved: Color? = null

            setContent { resolved = designSystem.resolveColor(M3Colors.Primary) }

            waitForIdle()
            assertEquals(FallbackColor, resolved)
        }

    @Test
    fun `dark mode resolves from the dark palette`() =
        runDesktopComposeUiTest {
            val theme =
                kompotTheme("gold") {
                    light { color(M3Colors.Primary, "#B8860B") }
                    dark { color(M3Colors.Primary, "#FFD770") }
                }
            var light: Color? = null
            var dark: Color? = null

            setContent {
                light = RemoteThemeDesignSystem(theme, FakeDesignSystem(), darkModeOverride = false).resolveColor(M3Colors.Primary)
                dark = RemoteThemeDesignSystem(theme, FakeDesignSystem(), darkModeOverride = true).resolveColor(M3Colors.Primary)
            }

            waitForIdle()
            assertEquals(Color(0xFFB8860B), light)
            assertEquals(Color(0xFFFFD770), dark)
        }

    // A light palette is NOT substituted in dark mode: a brand's near-white background under the
    // light text of the built-in dark theme is an unreadable screen, not a partial brand.
    @Test
    fun `a theme with no dark palette leaves dark mode entirely to the fallback`() =
        runDesktopComposeUiTest {
            val fallback = FakeDesignSystem()
            val designSystem =
                RemoteThemeDesignSystem(
                    theme = kompotTheme("light-only") { light { color(M3Colors.Primary, "#B8860B") } },
                    fallback = fallback,
                    darkModeOverride = true,
                )
            var resolved: Color? = null

            setContent { resolved = designSystem.resolveColor(M3Colors.Primary) }

            waitForIdle()
            assertEquals(FallbackColor, resolved)
        }

    @Test
    fun `typography overrides only the properties the theme actually set`() =
        runDesktopComposeUiTest {
            val designSystem =
                RemoteThemeDesignSystem(
                    theme = kompotTheme("gold") { typography { style(M3Typography.TitleLarge, fontSizeSp = 26f) } },
                    fallback = FakeDesignSystem(),
                    darkModeOverride = false,
                )
            var resolved: TextStyle? = null

            setContent { resolved = designSystem.resolveTypography(M3Typography.TitleLarge) }

            waitForIdle()
            val style = requireNotNull(resolved)
            assertEquals(26.sp, style.fontSize)
            assertEquals(FallbackStyle.lineHeight, style.lineHeight)
            assertEquals(FallbackStyle.fontWeight, style.fontWeight)
            assertEquals(FallbackStyle.letterSpacing, style.letterSpacing)
        }

    @Test
    fun `every typography property the theme sets reaches the resolved style`() =
        runDesktopComposeUiTest {
            val designSystem =
                RemoteThemeDesignSystem(
                    theme =
                        kompotTheme("gold") {
                            typography {
                                style(
                                    M3Typography.TitleLarge,
                                    fontSizeSp = 26f,
                                    lineHeightSp = 32f,
                                    fontWeight = 700,
                                    letterSpacingSp = 1.5f,
                                    color = "#B8860B",
                                )
                            }
                        },
                    fallback = FakeDesignSystem(),
                    darkModeOverride = false,
                )
            var resolved: TextStyle? = null

            setContent { resolved = designSystem.resolveTypography(M3Typography.TitleLarge) }

            waitForIdle()
            val style = requireNotNull(resolved)
            assertEquals(26.sp, style.fontSize)
            assertEquals(32.sp, style.lineHeight)
            assertEquals(FontWeight(700), style.fontWeight)
            assertEquals(1.5.sp, style.letterSpacing)
            assertEquals(Color(0xFFB8860B), style.color)
        }

    // The half of a theme that could not travel: a brand could restyle the size, weight and tracking
    // of every text on a screen and could not change one of their colours.
    @Test
    fun `a colour named inside a typography style repaints the text`() =
        runDesktopComposeUiTest {
            val designSystem =
                RemoteThemeDesignSystem(
                    theme = kompotTheme("gold") { typography { style(M3Typography.TitleLarge, color = "#B8860B") } },
                    fallback = FakeDesignSystem(),
                    darkModeOverride = false,
                )
            var resolved: TextStyle? = null

            setContent { resolved = designSystem.resolveTypography(M3Typography.TitleLarge) }

            waitForIdle()
            val style = requireNotNull(resolved)
            assertEquals(Color(0xFFB8860B), style.color)
            assertEquals(FallbackStyle.fontSize, style.fontSize)
        }

    // Same treatment as a malformed palette entry: the theme keeps working and that one value is
    // ignored, rather than the text being painted with a parse failure.
    @Test
    fun `a malformed colour inside a typography style leaves the built-in one`() =
        runDesktopComposeUiTest {
            val designSystem =
                RemoteThemeDesignSystem(
                    theme = kompotTheme("broken") { typography { style(M3Typography.TitleLarge, color = "rebeccapurple") } },
                    fallback = FakeDesignSystem(),
                    darkModeOverride = false,
                )
            var resolved: TextStyle? = null

            setContent { resolved = designSystem.resolveTypography(M3Typography.TitleLarge) }

            waitForIdle()
            assertEquals(FallbackStyle.color, requireNotNull(resolved).color)
        }

    @Test
    fun `a typography token the theme leaves out is returned by the fallback untouched`() =
        runDesktopComposeUiTest {
            val fallback = FakeDesignSystem()
            val designSystem = RemoteThemeDesignSystem(kompotTheme("empty") {}, fallback, darkModeOverride = false)
            var resolved: TextStyle? = null

            setContent { resolved = designSystem.resolveTypography(M3Typography.BodyMedium) }

            waitForIdle()
            assertEquals(FallbackStyle, resolved)
            assertEquals(listOf(M3Typography.BodyMedium), fallback.requestedStyles)
        }

    // Until the theme arrives — or if the request for it fails — the application runs on the built-in
    // design system, and the first frame does not wait for the network.
    @Test
    fun `rememberKompotDesignSystem returns the fallback itself while the theme is missing`() =
        runDesktopComposeUiTest {
            val fallback = FakeDesignSystem()
            var resolved: KompotDesignSystem? = null

            setContent { resolved = rememberKompotDesignSystem(theme = null, fallback = fallback) }

            waitForIdle()
            assertSame(fallback, resolved)
        }

    @Test
    fun `rememberKompotDesignSystem wraps the theme once it arrives`() =
        runDesktopComposeUiTest {
            var resolved: KompotDesignSystem? = null

            setContent {
                resolved =
                    rememberKompotDesignSystem(
                        theme = kompotTheme("gold") { light { color(M3Colors.Primary, "#B8860B") } },
                        fallback = FakeDesignSystem(),
                    )
            }

            waitForIdle()
            assertTrue(resolved is RemoteThemeDesignSystem)
        }
}
