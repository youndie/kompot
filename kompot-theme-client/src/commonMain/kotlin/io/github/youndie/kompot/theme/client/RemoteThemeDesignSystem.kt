package io.github.youndie.kompot.theme.client

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.youndie.kompot.KompotDesignSystem
import io.github.youndie.kompot.ColorToken
import io.github.youndie.kompot.TypographyToken
import io.github.youndie.kompot.theme.KompotTextStyle
import io.github.youndie.kompot.theme.KompotTheme
import io.github.youndie.kompot.theme.parseArgbHex

// A design system that asks the theme delivered by the server first and falls back — usually to the
// Material3 one — only for what the theme did not describe. An overlay, not a replacement: a theme
// that redefines three tokens out of twenty is a valid theme rather than a broken screen, and a
// malformed hex in one colour does not drag the rest down with it. The same degradation principle as
// UnknownComponent in :kompot-core.
//
// Renderers know nothing about this class: they read LocalKompotDesignSystem exactly as before, and
// the substitution is one line at the application's composition root.
class RemoteThemeDesignSystem(
    private val theme: KompotTheme,
    private val fallback: KompotDesignSystem,
    // Normally null, so the system setting decides. An explicit value is for tests and previews,
    // where there is no real system signal.
    private val darkModeOverride: Boolean? = null,
) : KompotDesignSystem {
    @Composable
    private fun darkMode(): Boolean = darkModeOverride ?: isSystemInDarkTheme()

    @Composable
    override fun resolveColor(token: ColorToken): Color {
        val argb = theme.colorFor(token, darkMode()) ?: return fallback.resolveColor(token)
        return Color(argb)
    }

    @Composable
    override fun resolveTypography(token: TypographyToken): TextStyle {
        val base = fallback.resolveTypography(token)
        val overrides = theme.styleFor(token) ?: return base
        return base.mergeWith(overrides)
    }
}

// A property left null keeps the built-in design system's value: a theme may raise a heading's size
// without redescribing the font family and everything else a KompotTextStyle does not even carry.
private fun TextStyle.mergeWith(overrides: KompotTextStyle): TextStyle =
    copy(
        // Same treatment as a palette entry: a value that does not parse leaves the built-in colour
        // alone rather than painting the text with a failure.
        color = overrides.color?.let(::parseArgbHex)?.let(::Color) ?: color,
        fontSize = overrides.fontSizeSp?.sp ?: fontSize,
        lineHeight = overrides.lineHeightSp?.sp ?: lineHeight,
        fontWeight = overrides.fontWeight?.let(::FontWeight) ?: fontWeight,
        letterSpacing = overrides.letterSpacingSp?.sp ?: letterSpacing,
    )

// The entry point for an application. Until the theme arrives — or if the request for it fails — the
// built-in design system is what draws, so the first frame does not wait for the network and an
// unreachable theme endpoint does not stop the application from starting. Once the theme arrives, the
// composition re-reads LocalKompotDesignSystem and recolours itself.
@Composable
fun rememberKompotDesignSystem(
    theme: KompotTheme?,
    fallback: KompotDesignSystem,
): KompotDesignSystem =
    remember(theme, fallback) {
        theme?.let { RemoteThemeDesignSystem(it, fallback) } ?: fallback
    }
