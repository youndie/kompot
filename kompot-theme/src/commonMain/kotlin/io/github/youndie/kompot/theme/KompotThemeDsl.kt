package io.github.youndie.kompot.theme

import io.github.youndie.kompot.ColorToken
import io.github.youndie.kompot.TypographyToken

// The backend-side DSL for assembling a theme, on the same principle as the screen and form
// builders: the data class constructor stays public, but describing a theme by hand — a
// Map<String, String> of text keys — makes a typo in a token far too easy. Here the key arrives
// typed as a ColorToken or TypographyToken, usually a ready-made constant from :kompot-ds-material.
@DslMarker
public annotation class KompotThemeDsl

public fun kompotTheme(
    id: String,
    block: KompotThemeBuilder.() -> Unit,
): KompotTheme = KompotThemeBuilder(id).apply(block).build()

@KompotThemeDsl
public class KompotThemeBuilder(
    private val id: String,
) {
    private var light = KompotPalette()
    private var dark: KompotPalette? = null
    private val typography = mutableMapOf<String, KompotTextStyle>()

    public fun light(block: KompotPaletteBuilder.() -> Unit) {
        light = KompotPaletteBuilder().apply(block).build()
    }

    // Not calling this is a valid decision: the client then stays entirely on its own palette in
    // dark mode (see the comment on KompotTheme.dark).
    public fun dark(block: KompotPaletteBuilder.() -> Unit) {
        dark = KompotPaletteBuilder().apply(block).build()
    }

    public fun typography(block: KompotTypographyBuilder.() -> Unit) {
        typography += KompotTypographyBuilder().apply(block).build()
    }

    public fun build(): KompotTheme = KompotTheme(id = id, light = light, dark = dark, typography = typography.toMap())
}

@KompotThemeDsl
public class KompotPaletteBuilder {
    private val colors = mutableMapOf<String, String>()

    public fun color(
        token: ColorToken,
        hex: String,
    ) {
        colors[token.key] = hex
    }

    public fun build(): KompotPalette = KompotPalette(colors.toMap())
}

@KompotThemeDsl
public class KompotTypographyBuilder {
    private val styles = mutableMapOf<String, KompotTextStyle>()

    // Every parameter is optional by design: an unset property is taken from the client's built-in
    // design system (see KompotTextStyle).
    public fun style(
        token: TypographyToken,
        fontSizeSp: Float? = null,
        lineHeightSp: Float? = null,
        fontWeight: Int? = null,
        letterSpacingSp: Float? = null,
        // A hex string, checked the same way the palette's values are: malformed leaves the built-in
        // colour rather than failing the theme.
        color: String? = null,
    ) {
        styles[token.key] =
            KompotTextStyle(
                color = color,
                fontSizeSp = fontSizeSp,
                lineHeightSp = lineHeightSp,
                fontWeight = fontWeight,
                letterSpacingSp = letterSpacingSp,
            )
    }

    public fun build(): Map<String, KompotTextStyle> = styles.toMap()
}
