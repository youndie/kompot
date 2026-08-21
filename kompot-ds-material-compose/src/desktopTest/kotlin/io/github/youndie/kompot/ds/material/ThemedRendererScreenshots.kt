package io.github.youndie.kompot.ds.material

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import io.github.youndie.kompot.KompotDesignSystem
import io.github.youndie.kompot.KompotModifierNode
import io.github.youndie.kompot.ColumnRenderer
import io.github.youndie.kompot.LocalKompotDesignSystem
import io.github.youndie.kompot.LocalKompotRegistry
import io.github.youndie.kompot.SizeType
import io.github.youndie.kompot.material3.M3Colors
import io.github.youndie.kompot.material3.M3Typography
import io.github.youndie.kompot.standard.ButtonComponent
import io.github.youndie.kompot.standard.CloseAction
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.TextComponent
import io.github.youndie.kompot.theme.KompotTheme
import io.github.youndie.kompot.theme.kompotTheme
import io.github.youndie.kompot.theme.client.RemoteThemeDesignSystem
import ru.workinprogress.viddik.annotations.ViddikScreenshot
import ru.workinprogress.viddik.core.viddikTypography

// The one place where the point of a server-driven theme is visible: the SAME component tree
// photographed three times, and only the goldens differ. No component, no renderer and no token
// changes between the shots — only the KompotDesignSystem implementation put into
// LocalKompotDesignSystem, exactly as an application does at its composition root.
//
// The palette here is the test's own rather than one imported from an application: what the golden
// documents is the mechanism.
private val BRAND_THEME =
    kompotTheme("screenshot-brand") {
        light {
            color(M3Colors.Primary, "#00695C")
            color(M3Colors.OnPrimary, "#FFFFFF")
            color(M3Colors.Background, "#F5FBF8")
            color(M3Colors.OnBackground, "#171D1B")
        }
        dark {
            color(M3Colors.Primary, "#80D5C7")
            color(M3Colors.OnPrimary, "#003731")
            color(M3Colors.Background, "#0E1513")
            color(M3Colors.OnBackground, "#DEE4E1")
        }
        typography {
            style(M3Typography.HeadlineSmall, fontSizeSp = 26f, fontWeight = 600)
        }
    }

// The background and the heading are painted through tokens, the button through its own
// primary/onPrimary: together the shot catches colour, typography, and the fact that what the theme
// does not touch — the button's shape, the spacing — stays as it was.
private val THEMED_TREE =
    ColumnComponent(
        id = "themed_screen",
        modifiers =
            listOf(
                KompotModifierNode.Size(width = SizeType.Fill),
                KompotModifierNode.Background(M3Colors.Background),
                KompotModifierNode.Padding(all = 16),
            ),
        spacing = 12,
        children =
            listOf(
                TextComponent(id = "title", text = "Order summary", style = M3Typography.HeadlineSmall),
                TextComponent(id = "subtitle", text = "One screen, two brands", style = M3Typography.BodyMedium),
                ButtonComponent(id = "cta", text = "Place order", action = CloseAction),
            ),
    )

// Both ends of the theme at once, the way an application wires them: the design system paints what
// is marked up with tokens, while the ColorScheme paints the Material chrome — a button's fill, the
// default text colour — which renderers reach through MaterialTheme rather than the design system.
@Composable
private fun ThemedScreenshot(
    theme: KompotTheme?,
    darkMode: Boolean,
) {
    val base = if (darkMode) darkColorScheme() else lightColorScheme()
    val designSystem: KompotDesignSystem =
        theme?.let { RemoteThemeDesignSystem(it, Material3DesignSystem(), darkModeOverride = darkMode) }
            ?: Material3DesignSystem()
    val locals: List<ProvidedValue<*>> =
        listOf(
            LocalKompotDesignSystem provides designSystem,
            LocalKompotRegistry provides registry,
        )
    // The same bundled font as in RendererScreenshotTheme: without it the goldens of this file would
    // stay unportable even once the rest are fixed.
    MaterialTheme(
        colorScheme = theme?.toMaterialColorScheme(base, darkMode) ?: base,
        typography = viddikTypography(),
    ) {
        CompositionLocalProvider(*locals.toTypedArray()) {
            ColumnRenderer().Render(
                component = THEMED_TREE,
                actionHandler = recordingActionHandler(),
                formController = testFormController(),
            )
        }
    }
}

// The baseline: what this tree looks like with no theme from a server at all.
@ViddikScreenshot(name = "Theme - built-in Material3", group = "Theme", width = 420, height = 220)
@Composable
fun StockThemeScreenshot() {
    ThemedScreenshot(theme = null, darkMode = false)
}

@ViddikScreenshot(name = "Theme - remote brand, light", group = "Theme", width = 420, height = 220)
@Composable
fun RemoteBrandLightScreenshot() {
    ThemedScreenshot(theme = BRAND_THEME, darkMode = false)
}

@ViddikScreenshot(name = "Theme - remote brand, dark", group = "Theme", width = 420, height = 220)
@Composable
fun RemoteBrandDarkScreenshot() {
    ThemedScreenshot(theme = BRAND_THEME, darkMode = true)
}
