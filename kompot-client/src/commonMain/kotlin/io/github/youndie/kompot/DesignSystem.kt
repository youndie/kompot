package io.github.youndie.kompot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

    // The contract that resolves the string design-system tokens of :kompot-core into the real colours
    // and fonts of a concrete Compose toolkit. Renderers know nothing about Material 3 or any other
    // branding — they simply ask an implementation to decode a token, and a white label is plugged in
    // through LocalKompotDesignSystem without a renderer changing.
    //
    // Icons are deliberately not part of this: drawing an icon as a glyph string was removed in favour
    // of real images (see :kompot-images and its Coil renderer).
interface KompotDesignSystem {
    @Composable
    fun resolveColor(token: ColorToken): Color

    @Composable
    fun resolveTypography(token: TypographyToken): TextStyle
}

val LocalKompotDesignSystem =
    staticCompositionLocalOf<KompotDesignSystem> {
        error("LocalKompotDesignSystem not provided")
    }
