package io.github.youndie.kompot.studio.editor

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import io.github.youndie.kompot.studio.ui.studioColors
import org.jetbrains.jewel.foundation.theme.JewelTheme

// THE BODY, EDITABLE. The source of truth is the text — not a component, not a DSL — because the text
// is what the client receives, what a fixture stores and what a schema is checked against. v1 only
// read it; this is the half that types into it.
//
// BasicTextField over TextFieldState rather than the legacy value/onValueChange pair: the old
// VisualTransformation is known for offset-mapping bugs, and every feature here is offsets — a span
// per token, an underline at a parse failure, a caret placed on a node.
@Composable
internal fun BodyEditor(
    state: TextFieldState,
    lexed: LexedJson,
    // Where the parser gave up, if it did. Underlined rather than red-lettered: the text there is
    // usually correct and the character after it is missing.
    errorOffset: Int?,
    modifier: Modifier = Modifier,
    // The text of the node the tree selected, tinted: the caret says where a node starts, the tint
    // says where it ends, and a person editing a node needs the second more than the first.
    selectedRange: IntRange? = null,
) {
    val palette = if (JewelTheme.isDark) DarkPalette else LightPalette
    val selectionTint = studioColors().selection

    BasicTextField(
        state = state,
        modifier = modifier,
        textStyle =
            TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = palette.plain,
            ),
        cursorBrush = SolidColor(palette.plain),
        outputTransformation =
            remember(lexed, errorOffset, palette, selectedRange, selectionTint) {
                OutputTransformation {
                    if (selectedRange != null) {
                        val start = selectedRange.first.coerceIn(0, length)
                        val end = (selectedRange.last + 1).coerceIn(start, length)
                        if (end > start) addStyle(SpanStyle(background = selectionTint), start, end)
                    }

                    // Coerced against THIS buffer's length, not the lexed text's: a keystroke between
                    // the lex and the draw is the normal case, and a span past the end throws.
                    lexed.tokens.forEach { token ->
                        val start = token.start.coerceIn(0, length)
                        val end = token.end.coerceIn(start, length)
                        if (end > start) addStyle(palette.styleFor(token.kind), start, end)
                    }

                    if (errorOffset != null) {
                        val start = errorOffset.coerceIn(0, length)
                        val end = (errorOffset + 1).coerceIn(start, length)
                        if (end > start) addStyle(palette.error, start, end)
                    }
                }
            },
    )
}

private class EditorPalette(
    val plain: Color,
    key: Color,
    typeKey: Color,
    typeValue: Color,
    string: Color,
    number: Color,
    literal: Color,
    punctuation: Color,
    errorColour: Color,
) {
    private val styles =
        mapOf(
            TokenKind.KEY to SpanStyle(color = key),
            TokenKind.TYPE_KEY to SpanStyle(color = typeKey),
            TokenKind.TYPE_VALUE to SpanStyle(color = typeValue),
            TokenKind.STRING to SpanStyle(color = string),
            TokenKind.NUMBER to SpanStyle(color = number),
            TokenKind.LITERAL to SpanStyle(color = literal),
            TokenKind.PUNCTUATION to SpanStyle(color = punctuation),
        )

    val error: SpanStyle = SpanStyle(color = errorColour, textDecoration = TextDecoration.Underline)

    fun styleFor(kind: TokenKind): SpanStyle = styles.getValue(kind)
}

// Two palettes rather than one with alpha: the editor sits inside the IDE-styled chrome, which is
// light or dark, and a colour readable on one is not on the other. `type` and its value are the
// loudest thing in both, because they are what a reader looks for first in a kompot body.
private val LightPalette =
    EditorPalette(
        plain = Color(0xFF1F1F1F),
        key = Color(0xFF0033B3),
        typeKey = Color(0xFF7A3E9D),
        typeValue = Color(0xFF7A3E9D),
        string = Color(0xFF067D17),
        number = Color(0xFF1750EB),
        literal = Color(0xFF0033B3),
        punctuation = Color(0xFF6E6E6E),
        errorColour = Color(0xFFC7222D),
    )

private val DarkPalette =
    EditorPalette(
        plain = Color(0xFFBCBEC4),
        key = Color(0xFF9373A5),
        typeKey = Color(0xFFC77DBB),
        typeValue = Color(0xFFC77DBB),
        string = Color(0xFF6AAB73),
        number = Color(0xFF2AACB8),
        literal = Color(0xFFCF8E6D),
        punctuation = Color(0xFF9DA0A8),
        errorColour = Color(0xFFF75464),
    )
