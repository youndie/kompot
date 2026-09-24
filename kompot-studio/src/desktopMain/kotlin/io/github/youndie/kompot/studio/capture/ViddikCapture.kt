package io.github.youndie.kompot.studio.capture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import java.awt.image.BufferedImage

// The reflective binding to viddik. Everything that can be wrong with it is wrong at construction —
// a missing class, a signature that moved — so the window asks once and either has the buttons or
// does not, instead of finding out on a click.
internal class ViddikCapture(
    loader: ClassLoader,
) : FrameCapture {
    private val captureEngine = Class.forName(CAPTURE_ENGINE, true, loader)

    private val captureMethod =
        captureEngine.methods.single { method ->
            method.name == "captureComposable" && method.parameterCount == CAPTURE_ARITY
        }

    private val differClass = Class.forName(IMAGE_DIFFER, true, loader)
    private val differ = differClass.getField("INSTANCE").get(null)
    private val diffMethod = differClass.methods.single { it.name == "diff" && it.parameterCount == DIFF_ARITY }

    override fun capture(
        width: Int,
        height: Int,
        compositionLocals: List<ProvidedValue<*>>,
        content: @Composable () -> Unit,
    ): BufferedImage =
        // A @Composable () -> Unit is a Function2<Composer, Int, Unit> at runtime, which is exactly
        // what the parameter is: captureComposable is a plain function TAKING a composable, not a
        // composable itself, so there is no composer for reflection to have to supply.
        captureMethod.invoke(null, width, height, compositionLocals, FONT_SCALE, content) as BufferedImage

    override fun diff(
        expected: BufferedImage,
        actual: BufferedImage,
        channelTolerance: Int,
    ): FrameDiff {
        val result = diffMethod.invoke(differ, expected, actual, channelTolerance)
        val type = result.javaClass

        return FrameDiff(
            image = type.getMethod("getDiffImage").invoke(result) as BufferedImage,
            mismatchPercent = type.getMethod("getMismatchPercent").invoke(result) as Double,
            mismatchedPixels = type.getMethod("getMismatchedPixels").invoke(result) as Int,
        )
    }

    private companion object {
        const val CAPTURE_ENGINE = "io.github.youndie.viddik.core.CaptureEngineKt"
        const val IMAGE_DIFFER = "io.github.youndie.viddik.core.ImageDiffer"

        // The full overload rather than the $default bridge: the bridge's extra mask and marker are a
        // compiler detail, and depending on their shape is depending on a version.
        //
        // Five since viddik 0.6, which put `fontScale` between the locals and the content. The arity is
        // the only thing that tells two versions apart here — the old four-argument lookup found
        // nothing on 0.6, and the studio silently lost its capture buttons (FrameCaptureTest caught it).
        const val CAPTURE_ARITY = 5
        const val DIFF_ARITY = 3

        // The studio shows the body at the size the consumer's frame sets and never scales fonts on its
        // own; 1 is viddik's default and what the four-argument version always rendered at.
        const val FONT_SCALE = 1f
    }
}
