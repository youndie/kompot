package io.github.youndie.kompot.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import io.github.youndie.kompot.ColorToken
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import io.github.youndie.kompot.KompotActionHandler
import io.github.youndie.kompot.KompotDegradationKind
import io.github.youndie.kompot.LocalKompotDesignSystem
import io.github.youndie.kompot.LocalKompotRegistry
import io.github.youndie.kompot.ds.material.Material3DesignSystem
import io.github.youndie.kompot.preview.KompotPreview
import io.github.youndie.kompot.preview.KompotPreviewState
import io.github.youndie.kompot.studio.tree.withSelectionBorder

// THE RENDER PANE, and it is a file of its own for one reason: the window and the capture tests have
// to draw the SAME thing. A screenshot taken from a composition assembled beside the window's — even
// from the same few lines, retyped — answers a question about the copy rather than about the studio.
//
// The two CompositionLocals are provided ABOVE the frame and read back INSIDE it, which is what makes
// a consumer's existing screenshot frame usable unchanged. konekt's BrandFrame installs
// LocalKompotRegistry itself; a frame that installs nothing gets the configuration's registry and the
// stock Material design system. Reading them back rather than passing the configuration's straight to
// KompotPreview is the difference between "the frame decides what a brand looks like" and "the frame
// decorates something the studio already decided".
// THE STUDIO'S RENDER, OFFERED TO A TEST. Public because the studio's central claim — that it draws
// what the client draws — is only checkable from outside: a consumer captures this composable and
// diffs it against the golden its own screenshot suite recorded. Without it the claim could only be
// looked at, in a window, by whoever was looking.
//
// It is the SAME function the window uses, not a second one shaped like it. A screen assembled for
// tests beside the one the window draws would answer a question about the copy.
@Composable
public fun KompotStudioScreen(
    config: KompotStudioConfig,
    body: String,
    brand: String? = null,
    dark: Boolean = false,
    state: KompotPreviewState = KompotPreviewState(),
    modifier: Modifier = Modifier,
    onDegraded: (KompotDegradationKind, String) -> Unit = { _, _ -> },
) {
    StudioRenderPane(
        config = config,
        body = body,
        brand = brand,
        dark = dark,
        modifier = modifier,
        state = state,
        onDegraded = onDegraded,
    )
}

@Composable
internal fun StudioRenderPane(
    config: KompotStudioConfig,
    body: String,
    brand: String?,
    dark: Boolean,
    modifier: Modifier = Modifier,
    // The node picked in the tree, by its component id. Null means nothing is picked, and then no
    // renderer is wrapped at all — a preview that always went through a decorator would be a preview
    // of a slightly different composition than the one a golden photographs.
    selectedId: String? = null,
    // Which of a form's three pictures is being looked at. Default empty, which is what a screen that
    // is not a form is anyway.
    state: KompotPreviewState = KompotPreviewState(),
    // The size the screen is being looked at, so that a fixed-height design meets a short window HERE
    // rather than on somebody's phone.
    device: DevicePreset = DEVICE_PRESETS.first(),
    // Where a tap goes. Nowhere, by default — the same nothing a preview has always done — and the
    // window passes one that writes the action down.
    actionHandler: KompotActionHandler = KompotActionHandler {},
    onDegraded: (KompotDegradationKind, String) -> Unit,
) {
    DeviceFrame(device, modifier) {
        CompositionLocalProvider(
            LocalKompotRegistry provides config.registry,
            // A floor rather than a choice: LocalKompotDesignSystem errors when nobody provides it,
            // and a frame is allowed to install none.
            LocalKompotDesignSystem provides Material3DesignSystem(),
        ) {
            config.frame(brand, dark) {
                val designSystem = LocalKompotDesignSystem.current

                // THE SCREEN'S GROUND, painted here and not left to the frame. A brand frame paints
                // what its screenshots need and nothing more; under a window whose own theme is dark
                // the unpainted parts of a light screen came through dark, which is a picture of no
                // device anybody ships. The colour is the design system's own background token —
                // every system this toolkit ships answers it, and answers an unknown token with a
                // colour and a warning rather than an exception, which is the contract this relies on.
                val ground = designSystem.resolveColor(BACKGROUND)

                Box(Modifier.fillMaxSize().background(ground)) {
                // Read back from inside the frame and decorated HERE rather than above it: a frame
                // that installs its own registry — konekt's does — must be the one that gets
                // decorated, or the frame would quietly opt out of the highlight.
                val registry =
                    LocalKompotRegistry.current.let { installed ->
                        if (selectedId == null) {
                            installed
                        } else {
                            installed.decorated { it.withSelectionBorder(selectedId) }
                        }
                    }

                KompotPreview(
                    body = body,
                    registry = registry,
                    designSystem = designSystem,
                    state = state,
                    json = config.json,
                    actionHandler = actionHandler,
                    // Collecting rather than the default, which throws. The default is right for a
                    // golden and wrong for a window somebody is typing into: a half-written body
                    // degrades on every keystroke, and a preview that dies on the first one cannot be
                    // typed in at all.
                    onDegraded = onDegraded,
                    pageLoader = config.pageLoader,
                )
                }
            }
        }
    }
}

// The token every design system this toolkit ships resolves to its page colour.
private val BACKGROUND = ColorToken("background")
