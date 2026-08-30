package io.github.youndie.kompot.ds.material

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import io.github.youndie.kompot.ColorToken
import io.github.youndie.kompot.material3.M3Colors
import io.github.youndie.kompot.theme.KompotTheme

// The second half of a server-driven theme, without which the first looks half-finished.
//
// RemoteThemeDesignSystem repaints what the BACKEND marked up with tokens: a card's colour, a story's
// ring, a banner's background, a heading's typography. But renderers do not take the Material chrome
// from the design system — they read MaterialTheme directly. A button renderer never touches
// KompotDesignSystem at all, and a text renderer goes there only when the component carries a token.
//
// That is by design: :kompot-client knows nothing about Material3 and cannot, since the dependency
// runs the other way. So the bridge from a theme to a ColorScheme can only live here, in the module
// that knows Material3 AND sees a KompotTheme.
//
// The result: an application hands this scheme to MaterialTheme(colorScheme = ...), and the button,
// the input field and every other Material component are branded without a single renderer changing.
public fun KompotTheme.toMaterialColorScheme(
    base: ColorScheme,
    darkMode: Boolean,
): ColorScheme {
    // A token the theme leaves unset keeps the base scheme's slot untouched — the same overlay rather
    // than replacement as in RemoteThemeDesignSystem.
    fun slot(
        token: ColorToken,
        current: Color,
    ): Color = colorFor(token, darkMode)?.let(::Color) ?: current

    return base.copy(
        primary = slot(M3Colors.Primary, base.primary),
        onPrimary = slot(M3Colors.OnPrimary, base.onPrimary),
        primaryContainer = slot(M3Colors.PrimaryContainer, base.primaryContainer),
        onPrimaryContainer = slot(M3Colors.OnPrimaryContainer, base.onPrimaryContainer),
        secondary = slot(M3Colors.Secondary, base.secondary),
        onSecondary = slot(M3Colors.OnSecondary, base.onSecondary),
        secondaryContainer = slot(M3Colors.SecondaryContainer, base.secondaryContainer),
        onSecondaryContainer = slot(M3Colors.OnSecondaryContainer, base.onSecondaryContainer),
        surface = slot(M3Colors.Surface, base.surface),
        onSurface = slot(M3Colors.OnSurface, base.onSurface),
        surfaceVariant = slot(M3Colors.SurfaceVariant, base.surfaceVariant),
        onSurfaceVariant = slot(M3Colors.OnSurfaceVariant, base.onSurfaceVariant),
        background = slot(M3Colors.Background, base.background),
        onBackground = slot(M3Colors.OnBackground, base.onBackground),
        error = slot(M3Colors.Error, base.error),
        onError = slot(M3Colors.OnError, base.onError),
        errorContainer = slot(M3Colors.ErrorContainer, base.errorContainer),
        onErrorContainer = slot(M3Colors.OnErrorContainer, base.onErrorContainer),
        outline = slot(M3Colors.Outline, base.outline),
        outlineVariant = slot(M3Colors.OutlineVariant, base.outlineVariant),
    )
}

// The entry point for an application: with no theme — or none describing dark mode — this is the
// stock Material3 scheme for the mode in question.
@Composable
public fun rememberMaterialColorScheme(
    theme: KompotTheme?,
    darkMode: Boolean = isSystemInDarkTheme(),
): ColorScheme =
    remember(theme, darkMode) {
        val base = if (darkMode) darkColorScheme() else lightColorScheme()
        theme?.toMaterialColorScheme(base, darkMode) ?: base
    }
