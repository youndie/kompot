package io.github.youndie.kompot.studio.editor

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.foundation.rememberScrollState
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
    val colors = studioColors()
    val errorTint = colors.error.copy(alpha = 0.12f)

    // THE SELECTED NODE, as a band behind its lines rather than a tint on its characters: a tint the
    // width of the text is what a text selection looks like, and a person who has both on screen
    // cannot tell which one the keyboard will act on. The band is drawn from the layout, offset by
    // the field's own scroll, so it sits behind the lines wherever they have scrolled to.
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val scroll = rememberScrollState()
    val band = colors.selection.copy(alpha = 0.45f)
    val accent = colors.accent

    BasicTextField(
        state = state,
        modifier =
            // Clipped, because the band is as tall as the node and a node can run past the bottom
            // of the field: unclipped, it painted over the inspector under the text.
            modifier.clipToBounds().drawBehind {
                val layout = layoutResult ?: return@drawBehind
                val range = selectedRange ?: return@drawBehind
                val length = layout.layoutInput.text.length
                val start = range.first.coerceIn(0, length)
                val end = range.last.coerceIn(start, length)
                val top = layout.getLineTop(layout.getLineForOffset(start))
                val bottom = layout.getLineBottom(layout.getLineForOffset(end))
                translate(top = -scroll.value.toFloat()) {
                    drawRect(band, Offset(0f, top), Size(size.width, bottom - top))
                    drawRect(accent, Offset(0f, top), Size(2.dp.toPx(), bottom - top))
                }
            },
        scrollState = scroll,
        onTextLayout = { result -> layoutResult = result() },
        textStyle =
            TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = palette.plain,
            ),
        cursorBrush = SolidColor(palette.plain),
        outputTransformation =
            remember(lexed, errorOffset, palette, errorTint) {
                OutputTransformation {
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
                        // The whole line tinted and the one character underlined: the tint is what
                        // the eye finds from across the window, the underline is where to type.
                        val text = asCharSequence()
                        var lineStart = start
                        while (lineStart > 0 && text[lineStart - 1] != '\n') lineStart--
                        var lineEnd = start
                        while (lineEnd < length && text[lineEnd] != '\n') lineEnd++
                        if (lineEnd > lineStart) addStyle(SpanStyle(background = errorTint), lineStart, lineEnd)
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
