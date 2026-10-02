package io.github.youndie.kompot.studio.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.foundation.theme.JewelTheme

// THE STUDIO'S OWN COLOURS, by role, in both themes. Jewel's theme carries the controls' colours;
// these are the handful the studio draws with itself — a selected row, a divider, a status glyph —
// and they are the int-ui greys, blues, reds and yellows the design names, not values invented here.
// One place, so a panel can never carry a light-theme literal into the dark window again.
internal class StudioColors(
    val text: Color,
    val dim: Color,
    val line: Color,
    val controlLine: Color,
    val field: Color,
    val selection: Color,
    val hover: Color,
    val accent: Color,
    val error: Color,
    val warning: Color,
    val ok: Color,
    val badge: Color,
    val badgeText: Color,
    val warningBanner: Color,
    val warningBannerLine: Color,
    // What a popup card stands on: white on the light theme, the int-ui panel grey on the dark one.
    val popup: Color,
)

private val LIGHT =
    StudioColors(
        text = Color(0xFF1E1F22),
        dim = Color(0xFF6C707E),
        line = Color(0xFFEBECF0),
        controlLine = Color(0xFFC9CCD6),
        field = Color(0xFFFFFFFF),
        selection = Color(0xFFD4E2FF),
        hover = Color(0xFFEBECF0),
        accent = Color(0xFF3574F0),
        error = Color(0xFFC7222D),
        warning = Color(0xFFB57A1A),
        ok = Color(0xFF369650),
        badge = Color(0xFFEBECF0),
        badgeText = Color(0xFF1E1F22),
        warningBanner = Color(0xFFFBF1DF),
        warningBannerLine = Color(0xFFE2C48A),
        popup = Color.White,
    )

private val DARK =
    StudioColors(
        text = Color(0xFFDFE1E5),
        dim = Color(0xFF868A91),
        line = Color(0xFF393B40),
        controlLine = Color(0xFF4E5157),
        field = Color(0xFF1E1F22),
        selection = Color(0xFF2E436E),
        hover = Color(0xFF393B40),
        accent = Color(0xFF3574F0),
        error = Color(0xFFE37774),
        warning = Color(0xFFF2C55C),
        ok = Color(0xFF5FAD65),
        badge = Color(0xFF393B40),
        badgeText = Color(0xFFDFE1E5),
        warningBanner = Color(0xFF3D3223),
        warningBannerLine = Color(0xFF826A41),
        popup = Color(0xFF2B2D30),
    )

@Composable
internal fun studioColors(): StudioColors = if (JewelTheme.isDark) DARK else LIGHT
