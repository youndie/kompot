package io.github.youndie.kompot

import io.github.youndie.kompot.generated.generatedFormsClientRenderers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// A component and its renderer may live in different modules, and for anything with a server they
// have to: a renderer needs Compose, a server does not have it, and a component declared beside its
// renderer is a component the server cannot construct — which makes the one thing a server-driven
// component exists for unreachable.
//
// The toolkit's own forms pair is that split (:kompot-forms declares the components, this module
// declares the renderers), so the capability is exercised on every build. Exercised is not the same
// as held, though: nothing asserted it, and a change that quietly made the processor require both
// halves in one module would have broken every consumer with a server while this repository stayed
// green — the components would simply have moved here with the renderers and nobody would have
// noticed what was lost.
class CrossModuleRegistryTest {
    @Test
    fun `the generated registry pairs renderers here with components from another module`() {
        val pairs = generatedFormsClientRenderers
        assertTrue(pairs.isNotEmpty(), "the generated map is empty — this test would prove nothing about pairing")

        var checked = 0
        pairs.forEach { (component, renderer) ->
            // The package is the readable half of the claim: io.github.youndie.kompot.forms is
            // :kompot-forms, io.github.youndie.kompot is this module.
            assertEquals(
                "io.github.youndie.kompot.forms",
                component.qualifiedName?.substringBeforeLast('.'),
                "a form component should be declared in the Compose-free module a server can depend on",
            )

            // And the load-bearing half, since a package is only a convention: the two classes come
            // out of different compilation outputs, which is what "different module" actually means.
            val componentSource = component.java.protectionDomain?.codeSource?.location
            val rendererSource = renderer::class.java.protectionDomain?.codeSource?.location
            if (componentSource != null && rendererSource != null) {
                assertTrue(
                    componentSource != rendererSource,
                    "$component and $renderer were loaded from the same output ($componentSource) — the split is gone",
                )
                checked++
            }
        }

        assertTrue(checked > 0, "no pair could be traced to its compilation output — the check above ran on nothing")
    }
}
