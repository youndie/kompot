package io.github.youndie.kompot.studio.capture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import java.awt.image.BufferedImage

// TAKING THE PICTURE, and comparing it with the one that was agreed.
//
// The toolkit's own line: a preview and a golden are one input and two checks. The second one lives
// only in tests today, so the trip from "the screen is right now" to "the screen is recorded" goes
// through an IDE, an annotation and an environment variable — and most of the time it does not
// happen.
//
// THROUGH AN INTERFACE AND REFLECTION, not a dependency, and the reason is what the screenshot tester
// is made of: viddik-testing-core carries `compose.desktop.currentOs` and JUnit as `api`. Naming it
// here would pin a host in this published module's POM and put a test framework on the runtime
// classpath of a tool. So the consumer's application adds it as `runtimeOnly` — the way
// ViddikShowroomLauncher already loads a registry — and where it is absent the studio simply has no
// such buttons.
internal interface FrameCapture {
    fun capture(
        width: Int,
        height: Int,
        compositionLocals: List<ProvidedValue<*>>,
        content: @Composable () -> Unit,
    ): BufferedImage

    fun diff(
        expected: BufferedImage,
        actual: BufferedImage,
        channelTolerance: Int = DEFAULT_CHANNEL_TOLERANCE,
    ): FrameDiff

    companion object {
        // viddik's own default, repeated here because the reflective call cannot read a default
        // argument — a Kotlin default lives in the callee's synthetic bridge, not in the signature.
        const val DEFAULT_CHANNEL_TOLERANCE: Int = 0
    }
}

internal data class FrameDiff(
    val image: BufferedImage,
    val mismatchPercent: Double,
    val mismatchedPixels: Int,
)

// Present only when the screenshot tester is. Null is not a failure: it is a studio running in an
// application that never asked for goldens, and the window drops two buttons rather than an error.
internal fun frameCaptureOrNull(loader: ClassLoader = ViddikCapture::class.java.classLoader): FrameCapture? =
    runCatching { ViddikCapture(loader) }.getOrNull()
