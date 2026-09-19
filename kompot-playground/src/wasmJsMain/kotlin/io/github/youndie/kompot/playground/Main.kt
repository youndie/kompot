package io.github.youndie.kompot.playground

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import io.github.youndie.kompot.KompotRegistry
import io.github.youndie.kompot.ds.material.Material3DesignSystem
import io.github.youndie.kompot.kompotCoreRenderers
import io.github.youndie.kompot.kompotStandardRenderers
import io.github.youndie.kompot.preview.KompotPreview

// THE PAGE, and for now the shortest thing it can honestly be: a body on the left of nothing, drawn
// by the renderers a client ships. B-26 puts the editor beside it, B-27 the tree, B-29 the switch
// that is the whole reason the page exists.
//
// The registry is assembled exactly as a deployment assembles one — core renderers plus the standard
// set — because that is the claim the page makes: this is the client, not a drawing of it.
private val registry = KompotRegistry(kompotCoreRenderers + kompotStandardRenderers)

@OptIn(ExperimentalComposeUiApi::class)
public fun main() {
    ComposeViewport(viewportContainerId = "kompotPlayground") { Playground() }
}

@Composable
private fun Playground() {
    MaterialTheme {
        Surface {
            KompotPreview(
                body = SAMPLE_BODY,
                registry = registry,
                designSystem = Material3DesignSystem(),
                // NOT the default, and this is the one place the page must differ from a golden test.
                // KompotPreview fails loudly on degradation so that a screenshot never records a hole
                // as the expected picture; here a throw is a blank page in somebody's browser, which
                // tells them the toolkit is broken when what happened is that a type was unfamiliar.
                // The page's whole subject is degradation, so it reports and carries on — B-29 turns
                // this callback into the visible log.
                onDegraded = { kind, originalType -> println("[kompot] $kind: $originalType") },
            )
        }
    }
}
