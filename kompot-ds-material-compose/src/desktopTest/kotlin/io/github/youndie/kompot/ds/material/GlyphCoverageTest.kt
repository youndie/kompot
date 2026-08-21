package io.github.youndie.kompot.ds.material

import kotlin.test.Test
import kotlin.test.assertTrue
import ru.workinprogress.viddik.core.ViddikGlyphCoverage

// A character the bundled font lacks is drawn with a substituted system one, and that differs between
// platforms. From the outside it looks like a screenshot mismatch of a fraction of a percent — "it
// failed and nobody knows why" — and the usual cure is raising the tolerance, after which the test
// stops catching real regressions.
//
// That is exactly what happened here: the close cross of a banner was written with U+2715 from
// Dingbats, which Roboto does not have. The screenshot differed by 122 pixels out of 120,000, which
// read as nit-picking — while it actually meant the close button was drawn in a different font from
// the rest of the application.
//
// This test turns that failure from "mysterious pixels" into "this character, in this string".
class GlyphCoverageTest {
    @Test
    fun `every string the UI draws is covered by the bundled font`() {
        val missing =
            uiStrings
                .flatMap { text -> ViddikGlyphCoverage.missingGlyphs(text).map { text to it } }
                .map { (text, codepoint) ->
                    "U+%04X (%s) in \"%s\"".format(codepoint, String(Character.toChars(codepoint)), text)
                }

        assertTrue(
            missing.isEmpty(),
            "has no glyph in the bundled font and will be drawn with a system one, which differs between " +
                "platforms:\n" + missing.joinToString("\n"),
        )
    }

    private companion object {
        // The strings the renderers actually draw. The list is maintained by hand on purpose: the point
        // is not completeness but that a rare character — an arrow, a cross, a tick, a currency sign —
        // arrives here together with the code that introduces it.
        val uiStrings =
            listOf(
                // The close cross of the full-screen banner: the very case above.
                "×",
                // Typical screen text: Cyrillic, digits, percents, punctuation. The set is deliberately
                // wide in characters rather than meaningful — what is under test is font coverage.
                "10% off your orders",
                "Order from a template before the end of the month and get a bigger discount",
                "Orders and plans",
                "Nothing here yet",
                "Order for Ada",
                "Connect",
                "1 234,56 €",
            )
    }
}
