package io.github.youndie.kompot.studio

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

// THE SIZE THE SCREEN IS BEING LOOKED AT, and it is a switch rather than "make the window smaller"
// for a reason a run of the client shows every time: a fixed-height screen on a window shorter than
// the design squeezes its last children to nothing, and a column that fills the screen hands out a
// remainder that is not there. That failure is invisible at any other size and invisible in a golden
// taken at one — which is what a preset row is for.
internal data class DevicePreset(
    val label: String,
    val width: Int?,
    val height: Int?,
) {
    val isWindow: Boolean get() = width == null || height == null
}

internal val DEVICE_PRESETS: List<DevicePreset> =
    listOf(
        DevicePreset("Window", null, null),
        // The canvas size the design work is done at.
        DevicePreset("393×852", 393, 852),
        DevicePreset("360×640", 360, 640),
        DevicePreset("768×1024", 768, 1024),
    )

// Density 1, so that a design's CSS pixel and the frame's dp are the same number. Anything else makes
// "393 wide" mean two things at once — the canvas's and the host's — and the two only agree on a
// machine whose scaling happens to be 1.
@Composable
internal fun DeviceFrame(
    preset: DevicePreset,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (preset.isWindow) {
        Box(modifier) { content() }
        return
    }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1f)) {
            // requiredSize and not size: the point is to IGNORE what the pane offers. A screen that
            // overflows a 360-wide phone has to overflow here too, and clipToBounds is what makes the
            // overflow visible instead of letting it draw over the studio's own chrome.
            Box(Modifier.requiredSize(preset.width!!.dp, preset.height!!.dp).clipToBounds()) { content() }
        }
    }
}
