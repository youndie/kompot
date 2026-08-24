package io.github.youndie.kompot

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextStyle
import io.github.youndie.kompot.standard.TextComponent
import io.github.youndie.kompot.standard.TextSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The order of §6, checked where it becomes visible. A unit test over the resolution would pass while
// the renderer hands Text an unconditional colour argument and throws the answer away — which is the
// defect this order exists to prevent — so every case here asserts the pixels that were actually
// painted.
@OptIn(ExperimentalTestApi::class)
class TextColorTest {
    private val danger = ColorToken("danger")
    private val coloured = TypographyToken("coloured")
    private val plain = TypographyToken("plain")

    private val red = Color(0xFFD32F2F)
    private val blue = Color(0xFF1A56DB)

    private inner class ColouringDesignSystem : KompotDesignSystem {
        @Composable
        override fun resolveColor(token: ColorToken): Color = if (token == danger) red else Color.Magenta

        @Composable
        override fun resolveTypography(token: TypographyToken): TextStyle =
            if (token == coloured) TextStyle(color = blue) else TextStyle.Default
    }

    @Composable
    private fun Scene(component: TextComponent) {
        MaterialTheme {
            CompositionLocalProvider(
                LocalKompotDesignSystem provides ColouringDesignSystem(),
                LocalKompotRegistry provides KompotRegistry(emptyMap()),
            ) {
                TextRenderer().Render(
                    component = component,
                    actionHandler = recordingActionHandler(),
                    formController = testFormController(),
                )
            }
        }
    }

    // Glyph edges are blended with whatever is behind them, so a colour is only evidence when enough
    // pixels carry it exactly: the core of a stroke, not its antialiased rim. Fully transparent
    // pixels are the unpainted background and say nothing — reading one as black is how an earlier
    // attempt at this test "found" the same colour in two different labels.
    private fun ink(image: ImageBitmap): Map<Color, Int> {
        val pixels = image.toPixelMap()
        val counted = mutableMapOf<Color, Int>()
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                val pixel = pixels[x, y]
                if (pixel.alpha < 0.99f) continue
                counted[pixel] = (counted[pixel] ?: 0) + 1
            }
        }
        return counted.filterValues { it >= 8 }
    }


    @Test
    fun `the node's own colour token wins over the colour of its typography token`() =
        runDesktopComposeUiTest {
            setContent { Scene(TextComponent(id = "t", text = "Balance low", style = coloured, color = danger)) }

            val ink = ink(onNodeWithText("Balance low").captureToImage())

            assertTrue(red in ink, "expected the token's red among $ink")
            assertTrue(blue !in ink, "the typography colour must lose to the node's own token")
        }

    // The regression that made the rule worth writing down: an unconditional colour argument silently
    // drops what the design system said, and nothing is unknown, so nothing is logged.
    @Test
    fun `the colour of a typography token reaches the screen when the node names none`() =
        runDesktopComposeUiTest {
            setContent { Scene(TextComponent(id = "t", text = "Balance low", style = coloured)) }

            assertTrue(blue in ink(onNodeWithText("Balance low").captureToImage()))
        }

    @Test
    fun `text that names no colour at all is painted in the surface's foreground`() =
        runDesktopComposeUiTest {
            var onSurface = Color.Unspecified
            setContent {
                MaterialTheme {
                    onSurface = MaterialTheme.colorScheme.onSurface
                    CompositionLocalProvider(
                        LocalKompotDesignSystem provides ColouringDesignSystem(),
                        LocalKompotRegistry provides KompotRegistry(emptyMap()),
                    ) {
                        TextRenderer().Render(
                            component = TextComponent(id = "t", text = "Balance low", style = plain),
                            actionHandler = recordingActionHandler(),
                            formController = testFormController(),
                        )
                    }
                }
            }

            val ink = ink(onNodeWithText("Balance low").captureToImage())

            assertTrue(onSurface in ink, "expected the surface foreground $onSurface among $ink")
            assertTrue(red !in ink && blue !in ink)
        }

    @Test
    fun `a span carries its own colour inside a sentence that has another`() =
        runDesktopComposeUiTest {
            setContent {
                Scene(
                    TextComponent(
                        id = "t",
                        text = "Paid 12 EUR",
                        style = coloured,
                        spans =
                            listOf(
                                TextSpan(text = "Paid "),
                                TextSpan(text = "12 EUR", color = danger),
                            ),
                    ),
                )
            }

            val ink = ink(onNodeWithText("Paid 12 EUR").captureToImage())

            assertTrue(red in ink, "the span's own colour is missing from $ink")
            assertTrue(blue in ink, "the rest of the sentence must keep the node's colour")
        }

    // §6: a run that names nothing takes the node's colour, not the ambient default — otherwise a
    // highlighted sentence would lose its colour on the words that were not singled out.
    @Test
    fun `a span that names no colour takes the node's, not the surface's`() =
        runDesktopComposeUiTest {
            var onSurface = Color.Unspecified
            setContent {
                MaterialTheme {
                    onSurface = MaterialTheme.colorScheme.onSurface
                    CompositionLocalProvider(
                        LocalKompotDesignSystem provides ColouringDesignSystem(),
                        LocalKompotRegistry provides KompotRegistry(emptyMap()),
                    ) {
                        TextRenderer().Render(
                            component =
                                TextComponent(
                                    id = "t",
                                    text = "Paid 12 EUR",
                                    color = danger,
                                    spans = listOf(TextSpan(text = "Paid "), TextSpan(text = "12 EUR", style = plain)),
                                ),
                            actionHandler = recordingActionHandler(),
                            formController = testFormController(),
                        )
                    }
                }
            }

            val ink = ink(onNodeWithText("Paid 12 EUR").captureToImage())

            assertTrue(red in ink)
            assertEquals(false, onSurface in ink, "the styled run fell back to the surface colour")
        }
}
