package io.github.youndie.kompot

import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals

class VisualFormattingTest {
    @Test
    fun `MaskVisualTransformation interleaves raw digits with the mask's literal characters`() {
        val transformation = MaskVisualTransformation("+998 (##) ###-##-##")

        val transformed = transformation.filter(AnnotatedString("901234567"))

        assertEquals("+998 (90) 123-45-67", transformed.text.text)
    }

    @Test
    fun `MaskVisualTransformation stops at the first literal it cannot fill when raw input is shorter than the mask`() {
        val transformation = MaskVisualTransformation("+998 (##) ###-##-##")

        val transformed = transformation.filter(AnnotatedString("90"))

        assertEquals("+998 (90", transformed.text.text)
    }

    @Test
    fun `MaskVisualTransformation exposes rawLength as the count of placeholder characters`() {
        assertEquals(9, MaskVisualTransformation("+998 (##) ###-##-##").rawLength)
        assertEquals(0, MaskVisualTransformation("no placeholders here").rawLength)
    }

    @Test
    fun `MaskVisualTransformation truncates raw input longer than rawLength before filling the mask`() {
        val transformation = MaskVisualTransformation("##-##")

        val transformed = transformation.filter(AnnotatedString("123456789"))

        assertEquals("12-34", transformed.text.text)
    }

    @Test
    fun `MaskVisualTransformation offset mapping is consistent round-trip for a mid-string cursor`() {
        val transformation = MaskVisualTransformation("+998 (##) ###-##-##")
        val transformed = transformation.filter(AnnotatedString("901234567"))

            // The caret after the third raw digit ("901") must sit right after "90" and before "1"
        val transformedOffset = transformed.offsetMapping.originalToTransformed(3)
        assertEquals(3, transformed.offsetMapping.transformedToOriginal(transformedOffset))
    }

    @Test
    fun `AmountVisualTransformation groups digits by three from the right`() {
        val transformation = AmountVisualTransformation()

        assertEquals("1 500 000", transformation.filter(AnnotatedString("1500000")).text.text)
        assertEquals("500", transformation.filter(AnnotatedString("500")).text.text)
        assertEquals("", transformation.filter(AnnotatedString("")).text.text)
    }

    @Test
    fun `AmountVisualTransformation appends the currency suffix only when there is at least one digit`() {
        val transformation = AmountVisualTransformation(currencySuffix = "UZS")

        assertEquals("1 500 UZS", transformation.filter(AnnotatedString("1500")).text.text)
        assertEquals("", transformation.filter(AnnotatedString("")).text.text)
    }

    @Test
    fun `AmountVisualTransformation omits the suffix entirely when it is null or blank`() {
        assertEquals("1 500", AmountVisualTransformation(currencySuffix = null).filter(AnnotatedString("1500")).text.text)
        assertEquals("1 500", AmountVisualTransformation(currencySuffix = " ").filter(AnnotatedString("1500")).text.text)
    }

    // Where the symbol goes is a property of the currency: of five a product might ship, two write it
    // first. A field that can only append is right for some of them and wrong for the rest, quietly —
    // the amount is correct and the symbol sits on the wrong side of it.
    @Test
    fun `AmountVisualTransformation writes the symbol first when it is a prefix`() {
        val transformation = AmountVisualTransformation(currencyPrefix = "$")

        assertEquals("$ 1 500", transformation.filter(AnnotatedString("1500")).text.text)
        assertEquals("", transformation.filter(AnnotatedString("")).text.text)
    }

    // The wire says at most one is set, and both set is a server mistake rather than a state with a
    // meaning. The suffix wins because a client built before the prefix existed draws it regardless:
    // this is the precedence under which one payload looks the same on every client.
    @Test
    fun `AmountVisualTransformation draws the suffix when a caller sets both`() {
        val transformation = AmountVisualTransformation(currencySuffix = "UZS", currencyPrefix = "$")

        assertEquals("1 500 UZS", transformation.filter(AnnotatedString("1500")).text.text)
    }

    // The caret is the half a prefix breaks: every digit of the field moves right by the width of the
    // symbol, so a mapping that ignores it puts the cursor one place per character away from where it
    // was typed.
    @Test
    fun `AmountVisualTransformation offset mapping carries the width of a prefix`() {
        val transformed = AmountVisualTransformation(currencyPrefix = "$").filter(AnnotatedString("1500"))

            // "$ 1 500": the caret after all four digits sits at the end of the string.
        assertEquals(transformed.text.text.length, transformed.offsetMapping.originalToTransformed(4))
        assertEquals(4, transformed.offsetMapping.transformedToOriginal(transformed.text.text.length))
            // And the caret before the first digit is after the symbol, not at the very start.
        assertEquals(2, transformed.offsetMapping.originalToTransformed(0))
        assertEquals(0, transformed.offsetMapping.transformedToOriginal(2))
    }

    @Test
    fun `AmountVisualTransformation offset mapping is consistent round-trip across a grouping separator`() {
        val transformation = AmountVisualTransformation()
        val transformed = transformation.filter(AnnotatedString("1500000"))

            // The caret after all seven digits — the end of the raw number.
        val transformedOffset = transformed.offsetMapping.originalToTransformed(7)
        assertEquals(7, transformed.offsetMapping.transformedToOriginal(transformedOffset))
    }
}
