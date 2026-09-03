package ru.workinprogress.viddik.generated

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import ru.workinprogress.viddik.annotations.ViddikComponent

// A STAND-IN FOR WHAT KSP WRITES IN A CONSUMER'S BUILD, at the name and in the shape the generator of
// this toolkit's viddik line really produces — both read out of viddik-processor 0.1.1.8 rather than
// assumed from a newer version.
//
// It exists so that the reflective lookup is asserted against a real class instead of against a hope.
// The alternative is a test that passes because the registry is absent, which is the case the
// implementation handles by returning nothing — and would therefore pass without the code under test
// working at all.
object GeneratedViddikRegistry {
    val components: List<ViddikComponent> =
        listOf(
            ViddikComponent(name = "A", group = "Brand", width = 100, height = 200, content = { Marker() }),
            ViddikComponent(name = "B", group = "Brand", width = 100, height = 200, content = { Marker() }),
        )
}

@Composable
private fun Marker() {
    Box(androidx.compose.ui.Modifier)
}
