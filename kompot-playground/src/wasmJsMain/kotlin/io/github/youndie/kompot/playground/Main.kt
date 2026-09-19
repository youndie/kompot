package io.github.youndie.kompot.playground

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

// The entry point, and nothing else: what the page IS lives in App.kt, so that the one function the
// browser calls stays readable next to the id it is handed.
@OptIn(ExperimentalComposeUiApi::class)
public fun main() {
    ComposeViewport(viewportContainerId = "kompotPlayground") { PlaygroundApp() }
}
