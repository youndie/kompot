package io.github.youndie.kompot.studio.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The screenshot tester, reached without depending on it. Every assertion here is about the binding
// rather than about viddik: that it finds the functions, that it passes a composable lambda across
// the reflective call, and that its absence is a studio with two fewer buttons rather than a crash.
class FrameCaptureTest {
    @Test
    fun `the binding finds the tester on a classpath that has it`() {
        val capture = assertNotNull(frameCaptureOrNull(), "viddik is on this test classpath and was not found")

        val image = capture.capture(64, 64, emptyList()) { Box(Modifier.fillMaxSize().background(Color.Red)) }

        assertEquals(64, image.width)
        assertEquals(64, image.height)
        // The composable lambda made it across the reflective call and actually drew: a binding that
        // passed the wrong thing would come back with a blank frame rather than an exception.
        assertEquals(0xFF0000, image.getRGB(32, 32) and 0xFFFFFF)
    }

    @Test
    fun `a diff is empty for the same frame and not for a changed one`() {
        val capture = assertNotNull(frameCaptureOrNull())

        val red = capture.capture(32, 32, emptyList()) { Box(Modifier.fillMaxSize().background(Color.Red)) }
        val again = capture.capture(32, 32, emptyList()) { Box(Modifier.fillMaxSize().background(Color.Red)) }
        val blue = capture.capture(32, 32, emptyList()) { Box(Modifier.fillMaxSize().background(Color.Blue)) }

        // The same composition twice is the same picture — which is also a check on the tester's
        // determinism, the property the whole idea of a golden rests on.
        assertEquals(0.0, capture.diff(red, again).mismatchPercent)

        val changed = capture.diff(red, blue)
        assertTrue(changed.mismatchPercent > 0.0, "a repainted screen produced no difference")
        assertEquals(32 * 32, changed.mismatchedPixels)
    }

    @Test
    fun `without the tester on the classpath there is no capture and no failure`() {
        // The negative control, and it is the whole reason the binding is reflective: a published
        // module must not carry viddik, so the studio has to run in an application that never added
        // it. A loader that hides those classes is that application.
        val without =
            object : ClassLoader(FrameCaptureTest::class.java.classLoader) {
                override fun loadClass(
                    name: String,
                    resolve: Boolean,
                ): Class<*> {
                    if (name.startsWith("ru.workinprogress.viddik")) throw ClassNotFoundException(name)
                    return super.loadClass(name, resolve)
                }
            }

        assertNull(frameCaptureOrNull(without), "the studio claimed a tester it cannot reach")
    }
}
