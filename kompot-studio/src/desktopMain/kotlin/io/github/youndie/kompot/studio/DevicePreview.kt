package io.github.youndie.kompot.studio

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlin.math.roundToInt
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
//
// The frame is laid out at the preset's size and SCALED to what is being asked for: to fit the pane
// when nothing is asked (a tablet in a half-window is otherwise a tablet's top-left corner), or to
// the zoom somebody set. Scaling rather than resizing, because the screen has to be measured at the
// device's size to fail the way it fails on the device — a 768-wide frame squeezed to 500 is a
// different screen. What does not fit scrolls.
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun DeviceFrame(
    preset: DevicePreset,
    modifier: Modifier = Modifier,
    // Null is "fit the pane, never larger than 1:1"; a number is the scale to draw at.
    zoom: Float? = null,
    // The scale actually drawn at, so a zoom control can step from it rather than from a guess.
    onScale: (Float) -> Unit = {},
    content: @Composable () -> Unit,
) {
    if (preset.isWindow) {
        // The window preset fills the pane, so "the size" is the pane's own, and zooming it means
        // laying the screen out smaller and drawing it larger.
        BoxWithConstraints(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
            val scale = zoom ?: 1f
            SideEffect { onScale(scale) }
            val density = LocalDensity.current
            val width = with(density) { (constraints.maxWidth / scale).toDp() }
            val height = with(density) { (constraints.maxHeight / scale).toDp() }
            Box(Modifier.requiredSize(width, height).graphicsLayer { scaleX = scale; scaleY = scale }) { content() }
        }
        return
    }

    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
        val width = preset.width!!
        val height = preset.height!!
        // Density 1 inside: the frame's dp is a px, so the pane's px constraints compare directly.
        val fit = minOf(1f, constraints.maxWidth.toFloat() / width, constraints.maxHeight.toFloat() / height)
        val scale = zoom ?: fit
        SideEffect { onScale(scale) }

        // WHERE THE FRAME IS, when it is larger than the pane: `pan` is the content point shown at
        // the pane's top-left corner, clamped in layout so the frame never leaves a gap. Not a
        // ScrollState, because a zoom has to move the offset in the same frame the size changes —
        // a scroll state clamps to the size it last measured, and zooming through it lands on the
        // corner, which is exactly the complaint this replaces.
        var pan by remember { mutableStateOf(Offset.Zero) }
        var pointer by remember { mutableStateOf<Offset?>(null) }
        val placed = remember { FloatArray(2) }
        val previous = remember { floatArrayOf(scale) }
        if (previous[0] != scale) {
            // Zoom around the pointer, or around the pane's centre when the pointer is elsewhere
            // (the buttons under the frame): the content point under the anchor stays under it.
            val factor = scale / previous[0]
            val anchor = pointer ?: Offset(constraints.maxWidth / 2f, constraints.maxHeight / 2f)
            val under = anchor - Offset(placed[0], placed[1])
            pan = (under * factor) - anchor
            previous[0] = scale
        }

        CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1f)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .onPointerEvent(PointerEventType.Move) { pointer = it.changes.firstOrNull()?.position }
                    .onPointerEvent(PointerEventType.Exit) { pointer = null }
                    // The wheel pans the frame when the screen inside did not take the event: a
                    // list scrolls itself, and a frame beyond its list scrolls as a whole.
                    .onPointerEvent(PointerEventType.Scroll) { event ->
                        val change = event.changes.firstOrNull() ?: return@onPointerEvent
                        if (change.isConsumed || event.keyboardModifiers.isMetaPressed || event.keyboardModifiers.isCtrlPressed) return@onPointerEvent
                        pan += change.scrollDelta * WHEEL_PX
                    }
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(Constraints())
                        val viewport = IntSize(constraints.maxWidth, constraints.maxHeight)
                        val x = offsetFor(pan.x, placeable.width, viewport.width)
                        val y = offsetFor(pan.y, placeable.height, viewport.height)
                        placed[0] = x.toFloat()
                        placed[1] = y.toFloat()
                        layout(viewport.width, viewport.height) { placeable.place(x, y) }
                    },
            ) {
                Box(Modifier.size((width * scale).dp, (height * scale).dp), contentAlignment = Alignment.Center) {
                    // requiredSize and not size: the point is to IGNORE what the pane offers. A screen
                    // that overflows a 360-wide phone has to overflow here too, and the clip is what
                    // makes the overflow visible instead of letting it draw over the studio's chrome.
                    // A device has corners and an edge; a bare rectangle reads as a bug in the layout
                    // rather than as a phone. The corner is the frame's, not the screen's: content is
                    // clipped to it the way a real screen is.
                    Box(
                        Modifier
                            .requiredSize(width.dp, height.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            // A translucent grey rather than a theme colour: this frame is composed
                            // in tests and captures that install no window theme, and an edge that
                            // reads on both grounds needs no theme to be told which one it is on.
                            .border(1.dp, Color(0x33808080), RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp)),
                    ) { content() }
                }
            }
        }
    }
}

// Centred while it fits; otherwise the pan, clamped so the far edge never comes inside the pane.
private fun offsetFor(
    pan: Float,
    content: Int,
    viewport: Int,
): Int =
    if (content <= viewport) (viewport - content) / 2 else (-pan).roundToInt().coerceIn(viewport - content, 0)

// Wheel ticks arrive in lines; this is the line, in px, the frame moves per tick.
private const val WHEEL_PX = 24f
