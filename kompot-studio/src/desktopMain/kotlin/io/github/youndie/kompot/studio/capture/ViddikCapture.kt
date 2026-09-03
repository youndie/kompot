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
        captureMethod.invoke(null, width, height, compositionLocals, content) as BufferedImage

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
        const val CAPTURE_ENGINE = "ru.workinprogress.viddik.core.CaptureEngineKt"
        const val IMAGE_DIFFER = "ru.workinprogress.viddik.core.ImageDiffer"

        // The four-argument overload rather than the $default bridge: the bridge's extra mask and
        // marker are a compiler detail, and depending on their shape is depending on a version.
        const val CAPTURE_ARITY = 4
        const val DIFF_ARITY = 3
    }
}
