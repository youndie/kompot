package io.github.youndie.kompot

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
     * A general input mask of the form "+1 (###) ###-##-##", where "#" is a placeholder for the next
     * raw character and every other character is a literal inserted on the fly. An OffsetMapping keeps
     * the caret in step with the mask.
 */
class MaskVisualTransformation(
    private val mask: String,
    private val placeholder: Char = '#',
) : VisualTransformation {
    val rawLength: Int = mask.count { it == placeholder }

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = if (text.text.length > rawLength) text.text.take(rawLength) else text.text

        val output = StringBuilder()
        var rawIndex = 0
        var maskIndex = 0
        while (rawIndex < raw.length && maskIndex < mask.length) {
            if (mask[maskIndex] == placeholder) {
                output.append(raw[rawIndex])
                rawIndex++
            } else {
                output.append(mask[maskIndex])
            }
            maskIndex++
        }

        val offsetMapping =
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int {
                    val clamped = offset.coerceIn(0, raw.length)
                    if (clamped == 0) return 0
                    var seen = 0
                    for (i in mask.indices) {
                        if (mask[i] == placeholder) {
                            seen++
                            if (seen == clamped) return (i + 1).coerceAtMost(output.length)
                        }
                    }
                    return output.length
                }

                override fun transformedToOriginal(offset: Int): Int {
                    val clamped = offset.coerceIn(0, output.length)
                    var seen = 0
                    for (i in 0 until clamped) {
                        if (i < mask.length && mask[i] == placeholder) seen++
                    }
                    return seen.coerceAtMost(raw.length)
                }
            }

        return TransformedText(AnnotatedString(output.toString()), offsetMapping)
    }
}

/**
     * Visual formatting of an amount: digits grouped in threes from the right, plus an optional
     * currency suffix. The stored value stays a number with no spaces.
 */
class AmountVisualTransformation(
    private val currencySuffix: String? = null,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter { it.isDigit() }
        val n = digits.length

        val grouped = StringBuilder()
        val digitToOutput = IntArray(n + 1)
        for (i in digits.indices) {
            if (i != 0 && (n - i) % 3 == 0) {
                grouped.append(' ')
            }
            digitToOutput[i] = grouped.length
            grouped.append(digits[i])
        }
        digitToOutput[n] = grouped.length

        val suffixText = if (!currencySuffix.isNullOrBlank() && n > 0) " $currencySuffix" else ""
        val fullText = grouped.toString() + suffixText

        val offsetMapping =
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int = digitToOutput[offset.coerceIn(0, n)]

                override fun transformedToOriginal(offset: Int): Int {
                    val clamped = offset.coerceIn(0, grouped.length)
                    for (i in n downTo 0) {
                        if (digitToOutput[i] <= clamped) return i
                    }
                    return 0
                }
            }

        return TransformedText(AnnotatedString(fullText), offsetMapping)
    }
}
