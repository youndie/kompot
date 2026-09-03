package io.github.youndie.kompot.studio

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import io.github.youndie.kompot.KompotDegradationKind
import io.github.youndie.kompot.LocalKompotDesignSystem
import io.github.youndie.kompot.LocalKompotRegistry
import io.github.youndie.kompot.ds.material.Material3DesignSystem
import io.github.youndie.kompot.preview.KompotPreview
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
    onDegraded: (KompotDegradationKind, String) -> Unit,
) {
    Box(modifier) {
        CompositionLocalProvider(
            LocalKompotRegistry provides config.registry,
            // A floor rather than a choice: LocalKompotDesignSystem errors when nobody provides it,
            // and a frame is allowed to install none.
            LocalKompotDesignSystem provides Material3DesignSystem(),
        ) {
            config.frame(brand, dark) {
                val designSystem = LocalKompotDesignSystem.current
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
                    json = config.json,
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
