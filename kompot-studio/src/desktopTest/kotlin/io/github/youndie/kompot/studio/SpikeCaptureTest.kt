package io.github.youndie.kompot.studio

import ru.workinprogress.viddik.core.captureComposable
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFails
import kotlin.test.assertTrue
import kotlin.test.fail

// The spike's questions (2), (4) and (5), asked mechanically. Eyeballing a window answers them for
// the person who happened to look; a frame read pixel by pixel answers them for whoever reads this
// next, and it is the only way "the button is Material's purple and not Jewel's grey" is a fact
// rather than an impression.
class SpikeCaptureTest {
    @Test
    fun `the captured frame carries the material primary the render pane was themed with`() {
        val image =
            captureComposable(width = 420, height = 420, compositionLocals = emptyList()) {
                SpikeRenderPane(body = sample("spike-screen.json"), dark = false) { kind, type ->
                    fail("the standard renderers degraded on the sample body: $kind $type")
                }
            }

        val wanted = SPIKE_PRIMARY_RGB
        var matched = 0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (image.getRGB(x, y) and 0xFFFFFF == wanted) matched++
            }
        }

        // A button's fill, not a stray anti-aliased pixel: several hundred at this size, and the
        // number is a floor rather than a golden — this test is about which theme won, and a golden
        // would make it about layout as well.
        assertTrue(matched > 200, "expected the button to be filled with the frame's primary, matched $matched pixels")
    }

    @Test
    fun `a paginated_list body fails because the preview provides no page loader`() {
        val failure =
            assertFails {
                captureComposable(width = 420, height = 420, compositionLocals = emptyList()) {
                    SpikeRenderPane(body = sample("spike-paginated.json"), dark = false) { _, _ -> }
                }
            }

        // The exact seam B-02 closes: KompotPreview does not provide LocalKompotPageLoader, so the
        // one standard component that asks for one takes the screen down. Asserted rather than
        // described, so that closing B-02 makes this test fail and say so.
        assertContains(
            generateSequence(failure) { it.cause }.mapNotNull { it.message }.joinToString(" | "),
            "PageLoader",
        )
    }
}
