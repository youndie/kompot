package io.github.youndie.kompot.theme

import kotlinx.serialization.Serializable
import io.github.youndie.kompot.ColorToken
import io.github.youndie.kompot.TypographyToken

// A server-driven theme: the values a client resolves design-system string tokens into. The tokens
// were open strings already — a backend could send ColorToken("promo_gold") with no client release —
// but their RESOLUTION was baked into the client's design system, so an unfamiliar token fell back.
// This theme closes the other half: the server sends both the token and what to paint it with.
//
// The module deliberately carries no Compose dependency, because a headless server uses it too —
// it is the side that serves the theme. The Compose implementation on top lives in
// :kompot-theme-client.
//
// Map keys are plain strings rather than ColorToken/TypographyToken: these are JSON object keys, and
// pushing a value class through the wire buys nothing. Typed access comes from the accessors below
// and from the DSL (see KompotThemeDsl.kt).
@Serializable
data class KompotTheme(
    // A brand identifier, used only for diagnostics — which theme actually arrived. No behaviour
    // depends on it.
    val id: String,
    val light: KompotPalette = KompotPalette(),
    // null means "the brand described no dark theme": in dark mode the client stays entirely on its
    // built-in palette rather than substituting the light one, which would put a near-white brand
    // background under dark text. The same degradation principle as UnknownComponent — incomplete
    // data from the backend costs styling, not the screen.
    val dark: KompotPalette? = null,
    // Typography is theme-independent: one set for both light and dark.
    val typography: Map<String, KompotTextStyle> = emptyMap(),
) {
    fun paletteFor(darkMode: Boolean): KompotPalette? = if (darkMode) dark else light

    fun colorFor(
        token: ColorToken,
        darkMode: Boolean,
    ): Int? = paletteFor(darkMode)?.argbFor(token)

    fun styleFor(token: TypographyToken): KompotTextStyle? = typography[token.key]
}

@Serializable
data class KompotPalette(
    // Values are hex strings (#RGB, #RRGGBB, #AARRGGBB — see parseArgbHex) rather than a packed Int:
    // themes are written and read by hand, and an Int in JSON would need explaining through signed
    // overflow.
    val colors: Map<String, String> = emptyMap(),
) {
    // null for both a missing and a malformed token: the caller does the same thing in either case —
    // fall back to the built-in design system — and there is nothing on the client that could act on
    // a typo in a hex value anyway.
    fun argbFor(token: ColorToken): Int? = colors[token.key]?.let(::parseArgbHex)
}

// Overrides only those text-style properties the backend actually sent; everything left null stays
// with the built-in design system. A theme can therefore raise a heading's size without restating
// the font family, weight and line height.
@Serializable
data class KompotTextStyle(
    val fontSizeSp: Float? = null,
    val lineHeightSp: Float? = null,
    // A numeric weight in CSS/Compose FontWeight terms (400 regular, 700 bold) rather than an enum:
    // :kompot-theme must not know one toolkit's set of weights.
    val fontWeight: Int? = null,
    val letterSpacingSp: Float? = null,
    // The colour of text in this style, and the reason it is here rather than only on the component:
    // a theme could restyle the size, weight and tracking of every text on a screen and could not
    // change one of their colours. Repainting a product is what a server-driven theme is for, and
    // colour is the half of it that could not travel.
    //
    // A hex value like the palette's, not a token key, and for the same reason every other property
    // here is a value: this IS the resolution of a token. A style whose colour named another token
    // would resolve one name into another name.
    val color: String? = null,
)

// Parses a hex colour into packed ARGB. #RGB, #RRGGBB and #AARRGGBB are supported, with or without
// the hash, in any case; alpha defaults to opaque. Anything else — wrong length, non-hex characters —
// yields null, meaning "this token was not overridden by the theme".
//
// It lives here rather than in a Compose module precisely because it is the only non-trivial piece
// of format logic, and it must be testable without a rendering pipeline.
fun parseArgbHex(value: String): Int? {
    val hex = value.trim().removePrefix("#")
    if (hex.isEmpty() || hex.any { it.digitToIntOrNull(16) == null }) return null
    val argb =
        when (hex.length) {
            3 -> "FF" + hex.map { "$it$it" }.joinToString("")
            6 -> "FF$hex"
            8 -> hex
            else -> return null
        }
    // Through Long rather than Int: 0xFFRRGGBB does not fit a signed Int, and toIntOrNull(16) would
    // return null for every opaque colour. Truncating to the low 32 bits yields the signed ARGB.
    return argb.toLongOrNull(16)?.toInt()
}
