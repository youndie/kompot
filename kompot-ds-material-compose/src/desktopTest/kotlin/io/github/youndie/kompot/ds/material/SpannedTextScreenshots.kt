package io.github.youndie.kompot.ds.material

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
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
import io.github.youndie.kompot.standard.OpenUrlAction
import io.github.youndie.kompot.standard.TextComponent
import io.github.youndie.kompot.standard.TextSpan
import ru.workinprogress.viddik.annotations.ViddikScreenshot
import ru.workinprogress.viddik.core.viddikTypography

// A design system with tokens for the runs a piece of prose is made of. Copied from MaterialTheme so
// the bundled font survives — a bare TextStyle names no family and would put the golden back in the
// platform's hands.
private class ProseDesignSystem : KompotDesignSystem {
    @Composable
    override fun resolveColor(token: ColorToken): Color = Material3DesignSystem().resolveColor(token)

    @Composable
    override fun resolveTypography(token: TypographyToken): TextStyle =
        when (token.key) {
            "code" -> MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF7A3E00))
            "link" -> MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF1B3FA8), textDecoration = TextDecoration.Underline)
            else -> MaterialTheme.typography.bodyMedium
        }
}

private const val HEAD = "The check lives in "
private const val CODE = "modifier-missing-check"
private const val TAIL = ", and the reasoning is in "
private const val LINK = "the backlog item"
private const val WHOLE = HEAD + CODE + TAIL + LINK

// Prose written by a person — what a tracker, a feed or a knowledge base is mostly about. A node
// carried one style for the whole string, so markdown arrived with its asterisks still on it and a
// link was a pair of brackets and a URL.
//
// The two nodes below hold the SAME string: the upper one is what a client that knows nothing of
// spans draws, the lower one is what the spans make of it. That §14 requires the two to agree is what
// makes the flat one a faithful degradation rather than a different sentence.
private val PROSE =
    ColumnComponent(
        id = "prose",
        modifiers =
            listOf(
                KompotModifierNode.Size(width = SizeType.Fill),
                KompotModifierNode.Background(M3Colors.Background),
                KompotModifierNode.Padding(all = 12),
            ),
        spacing = 14,
        children =
            listOf(
                TextComponent(id = "flat", text = WHOLE),
                TextComponent(
                    id = "rich",
                    text = WHOLE,
                    spans =
                        listOf(
                            TextSpan(text = HEAD),
                            TextSpan(text = CODE, style = TypographyToken("code")),
                            TextSpan(text = TAIL),
                            TextSpan(text = LINK, style = TypographyToken("link"), action = OpenUrlAction("https://example.org/backlog/42")),
                        ),
                ),
            ),
    )

@ViddikScreenshot(name = "Text - a span carries its own style and an action", group = "Renderer", width = 420, height = 170)
@Composable
fun SpannedTextScreenshot() {
    MaterialTheme(typography = viddikTypography()) {
        CompositionLocalProvider(
            LocalKompotDesignSystem provides ProseDesignSystem(),
            LocalKompotRegistry provides registry,
        ) {
            ColumnRenderer().Render(
                component = PROSE,
                actionHandler = recordingActionHandler(),
                formController = testFormController(),
            )
        }
    }
}
