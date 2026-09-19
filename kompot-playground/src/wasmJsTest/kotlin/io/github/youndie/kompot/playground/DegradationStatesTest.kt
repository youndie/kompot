package io.github.youndie.kompot.playground

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.youndie.kompot.KompotDegradationSink
import io.github.youndie.kompot.ds.material.Material3DesignSystem
import io.github.youndie.kompot.preview.KompotPreview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// THE THREE STATES THE PAGE CLAIMS, held by a run rather than by somebody having looked.
//
// A showcase is an assertion about the protocol, and an assertion nobody checks rots more quietly
// than any other: rename a renderer, let the demo type slip into the "older client" serializers
// module, and the page goes on showing three screens that no longer differ for the reason it says.
//
// What is asserted is the SINK, not the canvas. Compose under wasm draws into a canvas where the only
// readable fact is that pixels exist, and — the practical half — a screenshot taken right after an
// interaction shows the previous frame, so a picture-based check reports yesterday's truth. The sink
// is where the mechanism speaks: "nothing was drawn for this type" and "something was drawn in its
// place" are exactly the two sentences the three states differ by.
@OptIn(ExperimentalTestApi::class)
class DegradationStatesTest {
    // What the page really renders with, taken from the same ClientMode the switch uses. A test that
    // built its own Json and its own registry would be checking a client nobody ships.
    // The assertions live INSIDE the composition block and the test returns what runComposeUiTest
    // returns. On this target that value is the promise the test framework awaits: a helper that
    // composed, dropped the promise and then read the log would read it before anything had been
    // composed — and "no degradation reported" is exactly what three of these tests could pass on.
    private fun checkingReports(
        mode: ClientMode,
        body: String,
        assertions: (List<String>) -> Unit,
    ) = runComposeUiTest {
        val log = DegradationLog()
        setContent {
            MaterialTheme {
                KompotPreview(
                    body = body,
                    registry = mode.registry,
                    designSystem = Material3DesignSystem(),
                    json = mode.json,
                    degradationSink = KompotDegradationSink { kind, type, drawn -> log.report(kind, type, drawn) },
                )
            }
        }
        waitForIdle()
        assertions(log.lines)
    }

    @Test
    fun `today's client understands the demo type and reports nothing`() =
        checkingReports(ClientMode.CURRENT, SAMPLE_BODY) { reported ->
            // isEmpty rather than assertEquals against an empty list: what the log hands out is a
            // snapshot list, whose equals is identity, so the comparison failed on an empty log. The
            // failure was loud, which is the right direction for a mistake in a test of emptiness.
            assertTrue(reported.isEmpty(), reported.toString())
        }

    @Test
    fun `an older client reports the unfamiliar type as skipped`() =
        checkingReports(ClientMode.OLDER, SAMPLE_BODY) { reported ->
            assertEquals(1, reported.size, reported.toString())
            assertTrue("promo_banner" in reported.single(), reported.toString())
            assertTrue("skipped" in reported.single(), reported.toString())
        }

    // The state the whole page exists for: the same client, the same unfamiliar type, and a server
    // that named an equivalent. The difference is visible only in the third fact the sink carries.
    @Test
    fun `the same client draws the equivalent the server named`() =
        checkingReports(ClientMode.OLDER_WITH_FALLBACK, SAMPLE_BODY.withServerFallback(true)) { reported ->
            assertEquals(1, reported.size, reported.toString())
            assertTrue("drawn through its fallback" in reported.single(), reported.toString())
        }

    // The fourth case, and the reason the page names it apart: here the type decodes and the registry
    // has no renderer for it — a build assembled without a plug-in rather than a client older than the
    // server.
    @Test
    fun `a build without the renderer reports a different kind`() =
        checkingReports(ClientMode.MISSING_RENDERER, SAMPLE_BODY) { reported ->
            assertEquals(1, reported.size, reported.toString())
            assertTrue("UNRENDERABLE_COMPONENT" in reported.single(), reported.toString())
        }

    // THE GUARD OVER THE EXAMPLES THEMSELVES. They are hand-written bodies, and a hand-written body
    // is exactly the kind of thing that goes stale silently: a wire name changes, the page goes on
    // showing a screen with a hole in it, and the hole looks like the very mechanism the page
    // demonstrates. Today's client must find nothing to degrade in any of them.
    @Test
    fun `every example is a body today's client fully understands`() =
        runComposeUiTest {
            EXAMPLES.forEach { example ->
                val log = DegradationLog()
                setContent {
                    MaterialTheme {
                        KompotPreview(
                            body = example.body,
                            registry = ClientMode.CURRENT.registry,
                            designSystem = Material3DesignSystem(),
                            json = ClientMode.CURRENT.json,
                            degradationSink = KompotDegradationSink { kind, type, drawn -> log.report(kind, type, drawn) },
                        )
                    }
                }
                waitForIdle()
                assertTrue(log.lines.isEmpty(), "${example.name}: ${log.lines}")
            }
        }

    // The half the switch does NOT do to the client, because it cannot: naming an equivalent is the
    // server's, so it happens to the body. Checked here because a transform that silently did nothing
    // would make the state above pass for the wrong reason — there would simply be no fallback to draw.
    @Test
    fun `the fallback the server names is added to the body and taken back out`() {
        val withFallback = SAMPLE_BODY.withServerFallback(true)
        assertTrue("\"fallback\"" in withFallback, withFallback)

        assertTrue("\"fallback\"" !in withFallback.withServerFallback(false))
    }
}
