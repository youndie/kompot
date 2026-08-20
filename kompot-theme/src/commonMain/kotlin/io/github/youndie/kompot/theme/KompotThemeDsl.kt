package io.github.youndie.kompot.theme

import io.github.youndie.kompot.ColorToken
import io.github.youndie.kompot.TypographyToken

// The backend-side DSL for assembling a theme, on the same principle as the screen and form
// builders: the data class constructor stays public, but describing a theme by hand — a
// Map<String, String> of text keys — makes a typo in a token far too easy. Here the key arrives
// typed as a ColorToken or TypographyToken, usually a ready-made constant from :kompot-ds-material.
@DslMarker
annotation class KompotThemeDsl

fun kompotTheme(
    id: String,
    block: KompotThemeBuilder.() -> Unit,
): KompotTheme = KompotThemeBuilder(id).apply(block).build()

@KompotThemeDsl
class KompotThemeBuilder(
    private val id: String,
) {
    private var light = KompotPalette()
    private var dark: KompotPalette? = null
    private val typography = mutableMapOf<String, KompotTextStyle>()

    fun light(block: KompotPaletteBuilder.() -> Unit) {
        light = KompotPaletteBuilder().apply(block).build()
    }

    // Not calling this is a valid decision: the client then stays entirely on its own palette in
    // dark mode (see the comment on KompotTheme.dark).
    fun dark(block: KompotPaletteBuilder.() -> Unit) {
        dark = KompotPaletteBuilder().apply(block).build()
    }

    fun typography(block: KompotTypographyBuilder.() -> Unit) {
        typography += KompotTypographyBuilder().apply(block).build()
    }

    fun build() = KompotTheme(id = id, light = light, dark = dark, typography = typography.toMap())
}

@KompotThemeDsl
class KompotPaletteBuilder {
    private val colors = mutableMapOf<String, String>()

    fun color(
        token: ColorToken,
        hex: String,
    ) {
        colors[token.key] = hex
    }

    fun build() = KompotPalette(colors.toMap())
}

@KompotThemeDsl
class KompotTypographyBuilder {
    private val styles = mutableMapOf<String, KompotTextStyle>()

    // Every parameter is optional by design: an unset property is taken from the client's built-in
    // design system (see KompotTextStyle).
    fun style(
        token: TypographyToken,
        fontSizeSp: Float? = null,
        lineHeightSp: Float? = null,
        fontWeight: Int? = null,
        letterSpacingSp: Float? = null,
    ) {
        styles[token.key] =
            KompotTextStyle(
                fontSizeSp = fontSizeSp,
                lineHeightSp = lineHeightSp,
                fontWeight = fontWeight,
                letterSpacingSp = letterSpacingSp,
            )
    }

    fun build(): Map<String, KompotTextStyle> = styles.toMap()
}
