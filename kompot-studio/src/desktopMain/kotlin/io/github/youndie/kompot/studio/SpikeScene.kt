package io.github.youndie.kompot.studio

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.youndie.kompot.KompotDegradationKind
import io.github.youndie.kompot.KompotRegistry
import io.github.youndie.kompot.ds.material.Material3DesignSystem
import io.github.youndie.kompot.kompotCoreRenderers
import io.github.youndie.kompot.kompotStandardRenderers
import io.github.youndie.kompot.preview.KompotPreview

// THE RENDER PANE, and it is a file of its own for one reason: the window and the capture test have
// to draw the SAME thing. A screenshot taken from a composition assembled beside the window's — even
// from the same three lines, retyped — answers a question about the copy rather than about the studio.

// The toolkit's own renderers. A consumer's registry replaces this wholesale (B-09); a spike has no
// consumer, and the standard set is what the toolkit can draw without one.
internal val spikeRegistry: KompotRegistry = KompotRegistry(kompotCoreRenderers + kompotStandardRenderers)

internal val spikeDesignSystem: Material3DesignSystem = Material3DesignSystem()

// A primary nobody could mistake for Jewel's blue-grey chrome. Question (2) of the spike is whether a
// kompot button takes its colour from the MaterialTheme around the render pane or from the Jewel
// theme around the window, and the two answers are only distinguishable if the Material one is
// unmistakable. The capture test asserts this exact value out of the frame.
internal val SPIKE_PRIMARY: Color = Color(0xFF7B1FA2)

// The same colour as an RGB int, for the frame the capture test reads: a BufferedImage hands back
// packed ARGB, and converting Color back at the assertion is where a rounding of the float channels
// would quietly turn "not equal" into the test's answer.
internal const val SPIKE_PRIMARY_RGB: Int = 0x7B1FA2

internal fun spikeColorScheme(dark: Boolean) =
    if (dark) darkColorScheme(primary = SPIKE_PRIMARY) else lightColorScheme(primary = SPIKE_PRIMARY)

// The consumer's frame, in the shape B-09 will give it: a composition that wraps the render and
// decides what a brand looks like. Here it is the default one — Material3 with the spike's palette —
// because the spike has no consumer to ask.
@Composable
internal fun SpikeFrame(
    dark: Boolean,
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = spikeColorScheme(dark)) {
        // A Surface and not a bare Box: outside one, LocalContentColor is black, and every control
        // that colours its own text draws black on dark. The window would look "almost right" in dark
        // mode and be wrong in the one way that is hard to see.
        Surface(modifier = Modifier.fillMaxSize()) { content() }
    }
}

// The whole of what the spike renders: a body, drawn by the real dispatch, inside the frame.
@Composable
internal fun SpikeRenderPane(
    body: String,
    dark: Boolean,
    modifier: Modifier = Modifier,
    onDegraded: (KompotDegradationKind, String) -> Unit,
) {
    Box(modifier) {
        SpikeFrame(dark) {
            KompotPreview(
                body = body,
                registry = spikeRegistry,
                designSystem = spikeDesignSystem,
                // Collecting rather than the default, which throws. The default is right for a golden
                // and wrong for a window somebody is typing into: a half-written body degrades on
                // every keystroke, and a preview that dies on the first one cannot be typed in at all.
                onDegraded = onDegraded,
            )
        }
    }
}
