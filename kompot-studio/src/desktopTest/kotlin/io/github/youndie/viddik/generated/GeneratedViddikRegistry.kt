package io.github.youndie.viddik.generated

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import io.github.youndie.viddik.annotations.ViddikComponent

// A STAND-IN FOR WHAT KSP WRITES IN A CONSUMER'S BUILD, at the name and in the shape the generator of
// this toolkit's viddik line really produces — both read off the registry viddik-processor 0.6.0
// generated for :kompot-ds-material-compose, rather than assumed. The package is the one the move to
// Maven Central gave viddik (`io.github.youndie.viddik`); up to 0.3.3 it was `ru.workinprogress.viddik`,
// and a stand-in left at the old name would have kept this test green against a class no consumer has.
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
