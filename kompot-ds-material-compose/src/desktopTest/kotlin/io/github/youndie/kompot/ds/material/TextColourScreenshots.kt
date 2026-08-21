package io.github.youndie.kompot.ds.material

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.youndie.kompot.ColorToken
import io.github.youndie.kompot.ColumnRenderer
import io.github.youndie.kompot.KompotDesignSystem
import io.github.youndie.kompot.KompotModifierNode
import io.github.youndie.kompot.LocalKompotDesignSystem
import io.github.youndie.kompot.LocalKompotRegistry
import io.github.youndie.kompot.SizeType
import io.github.youndie.kompot.TypographyToken
import io.github.youndie.kompot.material3.M3Colors
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.TextComponent
import ru.workinprogress.viddik.annotations.ViddikScreenshot
import ru.workinprogress.viddik.core.viddikTypography

// A design system whose typography tokens carry a colour, which is what an application writes and
// what the toolkit's own Material3 implementation does not do — every Material3 TextStyle leaves
// colour Unspecified, so the bug this shot guards could not be seen through it.
private class ColouredTypographyDesignSystem : KompotDesignSystem {
    @Composable
    override fun resolveColor(token: ColorToken): Color = Material3DesignSystem().resolveColor(token)

    @Composable
    override fun resolveTypography(token: TypographyToken): TextStyle =
        when (token.key) {
            "error" -> TextStyle(fontSize = 16.sp, color = Color(0xFFB3261E))
            "meta" -> TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF6750A4))
            "body" -> TextStyle(fontSize = 16.sp, color = Color(0xFF1D1B20))
            else -> TextStyle(fontSize = 16.sp)
        }
}

// Four lines that must not look alike. Before the fix all four rendered in one colour: the renderer
// passed an explicit `color` argument, which overrides whatever the resolved style carries, so size
// and weight survived and colour did not — and nothing warned, because nothing was unknown.
private val COLOURED_TEXT_TREE =
    ColumnComponent(
        id = "colours",
        modifiers =
            listOf(
                KompotModifierNode.Size(width = SizeType.Fill),
                KompotModifierNode.Background(M3Colors.Background),
                KompotModifierNode.Padding(all = 12),
            ),
        spacing = 6,
        children =
            listOf(
                TextComponent(id = "body", text = "body — dark grey", style = TypographyToken("body")),
                TextComponent(id = "error", text = "error — red", style = TypographyToken("error")),
                TextComponent(id = "meta", text = "meta — purple", style = TypographyToken("meta")),
                TextComponent(id = "plain", text = "no token at all", style = null),
            ),
    )

@ViddikScreenshot(name = "Text - a token's colour reaches the screen", group = "Renderer", width = 420, height = 130)
@Composable
fun TypographyTokenColourScreenshot() {
    MaterialTheme(typography = viddikTypography()) {
        CompositionLocalProvider(
            LocalKompotDesignSystem provides ColouredTypographyDesignSystem(),
            LocalKompotRegistry provides registry,
        ) {
            ColumnRenderer().Render(
                component = COLOURED_TEXT_TREE,
                actionHandler = recordingActionHandler(),
                formController = testFormController(),
            )
        }
    }
}
