package io.github.youndie.kompot.ds.material

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
import io.github.youndie.kompot.standard.TextSpan
import ru.workinprogress.viddik.annotations.ViddikScreenshot
import ru.workinprogress.viddik.core.viddikTypography

// A design system whose "quiet" typography token carries a colour of its own, so the picture shows
// the two sources of colour disagreeing rather than only one of them working.
private class TintedDesignSystem : KompotDesignSystem {
    @Composable
    override fun resolveColor(token: ColorToken): Color =
        when (token.key) {
            "danger" -> Color(0xFFB3261E)
            else -> Material3DesignSystem().resolveColor(token)
        }

    @Composable
    override fun resolveTypography(token: TypographyToken): TextStyle =
        when (token.key) {
            "quiet" -> MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF6F6F6F))
            "amount" -> MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
            else -> MaterialTheme.typography.bodyMedium
        }
}

private const val HEAD = "Payment of "
private const val AMOUNT = "12.40 EUR"
private const val TAIL = " was declined"

// The three lines are the three steps of §6, in order. Read downwards: the node's own token beats the
// colour of its typography token, a node that names none keeps the typography colour, and a run
// inside a sentence can differ from the sentence without a background behind it.
private val COLOURS =
    ColumnComponent(
        id = "colours",
        modifiers =
            listOf(
                KompotModifierNode.Size(width = SizeType.Fill),
                KompotModifierNode.Background(M3Colors.Background),
                KompotModifierNode.Padding(all = 12),
            ),
        spacing = 12,
        children =
            listOf(
                TextComponent(id = "own", text = "Its own token wins", style = TypographyToken("quiet"), color = ColorToken("danger")),
                TextComponent(id = "styled", text = "The typography token's colour", style = TypographyToken("quiet")),
                TextComponent(
                    id = "spans",
                    text = HEAD + AMOUNT + TAIL,
                    spans =
                        listOf(
                            TextSpan(text = HEAD),
                            TextSpan(text = AMOUNT, style = TypographyToken("amount"), color = ColorToken("danger")),
                            TextSpan(text = TAIL),
                        ),
                ),
            ),
    )

@ViddikScreenshot(name = "Text - a colour token on the node and on one run", group = "Renderer", width = 420, height = 150)
@Composable
fun TextColorScreenshot() {
    MaterialTheme(typography = viddikTypography()) {
        CompositionLocalProvider(
            LocalKompotDesignSystem provides TintedDesignSystem(),
            LocalKompotRegistry provides registry,
        ) {
            ColumnRenderer().Render(
                component = COLOURS,
                actionHandler = recordingActionHandler(),
                formController = testFormController(),
            )
        }
    }
}
