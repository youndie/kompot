package io.github.youndie.kompot.studio

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.youndie.kompot.LocalKompotDesignSystem
import io.github.youndie.kompot.ds.material.Material3DesignSystem
import io.github.youndie.kompot.theme.KompotPalette
import io.github.youndie.kompot.theme.KompotTheme
import ru.workinprogress.viddik.core.captureComposable
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFails
import kotlin.test.assertTrue
import kotlin.test.fail

// What the configuration is FOR, asked of pixels. Every claim here is about which composition decided
// a colour, and that is a question a golden cannot answer and a look at the window answers only for
// whoever looked.
class StudioRenderTest {
    @Test
    fun `a frame the consumer writes decides the colour a component is drawn in`() {
        // A frame in the shape a real one has: a MaterialTheme AND a design system, installed
        // together. konekt's BrandFrame is this plus its own registry and shape scale.
        val brandFrame: KompotStudioFrame = { brand, _, content ->
            val colour = Color(0xFF000000 or if (brand == "b") BRAND_B.toLong() else BRAND_A.toLong())
            MaterialTheme(colorScheme = lightColorScheme(primary = colour)) {
                Surface(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalKompotDesignSystem provides Material3DesignSystem()) {
                        content()
                    }
                }
            }
        }

        val config =
            KompotStudioConfig(
                registry = toolkitRegistry,
                frame = brandFrame,
                brands = listOf("a", "b"),
            )

        val onA = capture(config, brand = "a")
        val onB = capture(config, brand = "b")

        // Both directions, because either alone is satisfied by a frame that ignores its argument: one
        // says the brand's colour arrived, the other that the OTHER brand's did not.
        assertTrue(fill(onA, BRAND_A) > 200, "brand a did not paint the button: ${fill(onA, BRAND_A)} px")
        assertTrue(fill(onB, BRAND_B) > 200, "brand b did not paint the button: ${fill(onB, BRAND_B)} px")
        assertTrue(fill(onA, BRAND_B) == 0, "brand a's frame drew brand b's colour")
        assertTrue(fill(onB, BRAND_A) == 0, "brand b's frame drew brand a's colour")
    }

    @Test
    fun `the default frame paints a served theme's colours without the consumer writing one`() {
        val served =
            KompotTheme(
                id = "served",
                light = KompotPalette(colors = mapOf("primary" to "#FF00897B")),
            )

        val config =
            KompotStudioConfig(
                registry = toolkitRegistry,
                frame = kompotStudioFrame(mapOf("served" to served)),
                brands = listOf("served"),
            )

        val framed = capture(config, brand = "served")
        val unframed = capture(config, brand = null)

        assertTrue(
            fill(framed, SERVED_TEAL) > 200,
            "the served theme did not reach the button: ${fill(framed, SERVED_TEAL)} px",
        )
        // The control that stops this from being a test of "any colour at all": with no brand the
        // default frame falls back to stock Material3, which is not teal.
        assertTrue(fill(unframed, SERVED_TEAL) == 0, "a null brand still painted the served theme's colour")
    }

    @Test
    fun `a paginated_list body fails because the preview provides no page loader`() {
        val config = KompotStudioConfig(registry = toolkitRegistry)

        val failure =
            assertFails {
                capture(config, brand = null, body = sample("sample-paginated.json"))
            }

        // The exact seam B-02 closes: KompotPreview does not provide LocalKompotPageLoader, so the one
        // standard component that asks for one takes the screen down. Asserted rather than described,
        // so that closing B-02 makes this test fail and say so.
        assertContains(
            generateSequence(failure) { it.cause }.mapNotNull { it.message }.joinToString(" | "),
            "PageLoader",
        )
    }

    private fun capture(
        config: KompotStudioConfig,
        brand: String?,
        dark: Boolean = false,
        body: String = SAMPLE_BODY,
    ): BufferedImage =
        captureComposable(width = 420, height = 420, compositionLocals = emptyList()) {
            StudioRenderPane(config = config, body = body, brand = brand, dark = dark) { kind, type ->
                fail("the standard renderers degraded on the sample body: $kind $type")
            }
        }

    private fun fill(
        image: BufferedImage,
        rgb: Int,
    ): Int {
        var matched = 0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (image.getRGB(x, y) and 0xFFFFFF == rgb) matched++
            }
        }
        return matched
    }

    private companion object {
        // Colours no palette in this composition holds by accident: not Jewel's chrome, not stock
        // Material3's purple, and not each other. Kept as packed RGB, which is what a BufferedImage
        // hands back — converting a Color at the assertion is where a rounding of the float channels
        // would quietly become the test's answer.
        const val BRAND_A = 0x7B1FA2
        const val BRAND_B = 0xB71C1C
        const val SERVED_TEAL = 0x00897B
    }
}
