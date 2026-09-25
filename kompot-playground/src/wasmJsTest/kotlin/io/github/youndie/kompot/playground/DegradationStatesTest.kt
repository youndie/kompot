package io.github.youndie.kompot.playground

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.youndie.kompot.KompotDegradationSink
import io.github.youndie.kompot.ds.material.Material3DesignSystem
import io.github.youndie.kompot.preview.KompotPreview
import io.github.youndie.kompot.preview.decodeKompotBody
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
        client: PlaygroundClient,
        body: String,
        assertions: (List<String>) -> Unit,
    ) = runComposeUiTest {
        val log = DegradationLog()
        setContent {
            MaterialTheme {
                KompotPreview(
                    body = body,
                    registry = client.registry,
                    designSystem = Material3DesignSystem(),
                    json = client.json,
                    degradationSink = KompotDegradationSink { kind, type, outcome -> log.report(kind, type, outcome) },
                )
            }
        }
        waitForIdle()
        assertions(log.lines)
    }

    @Test
    fun `today's client understands the demo type and reports nothing`() =
        checkingReports(PlaygroundClient(ClientMode.CURRENT, PROMO), SAMPLE_BODY) { reported ->
            // isEmpty rather than assertEquals against an empty list: what the log hands out is a
            // snapshot list, whose equals is identity, so the comparison failed on an empty log. The
            // failure was loud, which is the right direction for a mistake in a test of emptiness.
            assertTrue(reported.isEmpty(), reported.toString())
        }

    @Test
    fun `an older client reports the unfamiliar type as skipped`() =
        checkingReports(PlaygroundClient(ClientMode.OLDER, PROMO), SAMPLE_BODY) { reported ->
            assertEquals(1, reported.size, reported.toString())
            assertTrue("promo_banner" in reported.single(), reported.toString())
            assertTrue("nothing" in reported.single(), reported.toString())
        }

    // The state the whole page exists for: the same client, the same unfamiliar type, and a server
    // that named an equivalent. The difference is visible only in the outcome the sink carries.
    @Test
    fun `the same client draws the equivalent the server named`() =
        checkingReports(PlaygroundClient(ClientMode.OLDER_WITH_FALLBACK, PROMO), SAMPLE_BODY.withServerFallback(true, PROMO)) { reported ->
            assertEquals(1, reported.size, reported.toString())
            assertTrue("server_fallback" in reported.single(), reported.toString())
        }

    // The fourth case, and the reason the page names it apart: here the type decodes and the registry
    // has no renderer for it — a build assembled without a plug-in rather than a client older than the
    // server.
    @Test
    fun `a build without the renderer reports a different kind`() =
        checkingReports(PlaygroundClient(ClientMode.MISSING_RENDERER, PROMO), SAMPLE_BODY) { reported ->
            assertEquals(1, reported.size, reported.toString())
            assertTrue("UNRENDERABLE_COMPONENT" in reported.single(), reported.toString())
            // The WIRE name, not PromoBanner: before B-34 this kind answered in Kotlin, which left the
            // node in the tree unmarkable and the log ungreppable by the only name its reader has.
            assertTrue("promo_banner" in reported.single(), reported.toString())
            assertTrue("placeholder" in reported.single(), reported.toString())
        }

    // THE GUARD OVER THE EXAMPLES THEMSELVES. They are hand-written bodies, and a hand-written body
    // is exactly the kind of thing that goes stale silently: a wire name changes, the page goes on
    // showing a screen with a hole in it, and the hole looks like the very mechanism the page
    // demonstrates. Today's client must find nothing to degrade in any of them.
    @Test
    fun `every example is a body today's client fully understands`() =
        runComposeUiTest {
            val today = PlaygroundClient(ClientMode.CURRENT, PROMO)
            EXAMPLES.forEach { example ->
                val log = DegradationLog()
                setContent {
                    MaterialTheme {
                        KompotPreview(
                            body = example.body,
                            registry = today.registry,
                            designSystem = Material3DesignSystem(),
                            json = today.json,
                            degradationSink = KompotDegradationSink { kind, type, outcome -> log.report(kind, type, outcome) },
                        )
                    }
                }
                waitForIdle()
                assertTrue(log.lines.isEmpty(), "${example.name}: ${log.lines}")
            }
        }

    // THE SAME THREE STATES FOR A STANDARD WORD (B-59). box came into the standard vocabulary in
    // 0.38, so its older client is not "a build without a plug-in" — it is today's client with one
    // registration taken out of a module generated whole. The log names the wire type the body sent.
    @Test
    fun `an older client skips box`() =
        checkingReports(PlaygroundClient(ClientMode.OLDER, BOX), LAYERS) { reported ->
            assertEquals(listOf("UNKNOWN_COMPONENT  \"box\"  nothing"), reported.toList())
        }

    @Test
    fun `the same client draws the equivalent the server named for box`() =
        checkingReports(PlaygroundClient(ClientMode.OLDER_WITH_FALLBACK, BOX), LAYERS.withServerFallback(true, BOX)) { reported ->
            assertEquals(listOf("UNKNOWN_COMPONENT  \"box\"  server_fallback"), reported.toList())
        }

    @Test
    fun `a build without the box renderer shows a placeholder`() =
        checkingReports(PlaygroundClient(ClientMode.MISSING_RENDERER, BOX), LAYERS) { reported ->
            assertEquals(listOf("UNRENDERABLE_COMPONENT  \"box\"  placeholder"), reported.toList())
        }

    // Taking one word away takes away exactly one: the rest of today's vocabulary still decodes, and
    // the unknown fallback the old client degrades through survived the copy.
    @Test
    fun `an older client differs from today's by the one word`() {
        val older = PlaygroundClient(ClientMode.OLDER, BOX).json
        val today = PlaygroundClient(ClientMode.CURRENT, BOX).json
        assertTrue(SAMPLE_BODY.componentTypes().isNotEmpty())
        assertEquals(today.decodeKompotBody(SAMPLE_BODY).screen, older.decodeKompotBody(SAMPLE_BODY).screen, "no box in the sample: the two agree")
        assertTrue(today.decodeKompotBody(LAYERS).screen != older.decodeKompotBody(LAYERS).screen, "the layers example has box: they differ")
    }

    @Test
    fun `the switch offers the component types the body sends`() {
        val words = LAYERS.componentTypes()
        assertTrue(BOX in words, words.toString())
        assertTrue("size" !in words && "padding" !in words, "modifiers are not components: $words")
        assertTrue(PROMO in SAMPLE_BODY.componentTypes(), SAMPLE_BODY.componentTypes().toString())
    }

    @Test
    fun `the word defaults to the rarest type below the root and survives a body that still sends it`() {
        assertEquals(PROMO, SAMPLE_BODY.chooseWord(null))
        assertEquals(BOX, LAYERS.chooseWord(null))
        assertEquals("text", LAYERS.chooseWord("text"), "a chosen word the body still sends is kept")
        assertEquals(BOX, LAYERS.chooseWord(PROMO), "one it no longer sends is not")
    }

    // Choosing another word moves the server's half: the equivalent leaves the old type's nodes. A
    // fallback the author wrote is not ours and stays.
    @Test
    fun `the equivalent moves with the chosen word and leaves an authored fallback alone`() {
        val onBox = LAYERS.withServerFallback(true, BOX)
        assertTrue("card-equivalent" in onBox, onBox)

        val onText = onBox.withServerFallback(true, "text")
        assertTrue("card-equivalent" !in onText, onText)
        assertTrue("title-equivalent" in onText, onText)

        val authored = """{"type":"box","id":"b","children":[],"fallback":{"type":"text","id":"mine","text":"hi"}}"""
        assertTrue("\"mine\"" in authored.withServerFallback(true, BOX))
        assertTrue("\"mine\"" in authored.withServerFallback(false, BOX))
    }

    // The half the switch does NOT do to the client, because it cannot: naming an equivalent is the
    // server's, so it happens to the body. Checked here because a transform that silently did nothing
    // would make the state above pass for the wrong reason — there would simply be no fallback to draw.
    @Test
    fun `the fallback the server names is added to the body and taken back out`() {
        val withFallback = SAMPLE_BODY.withServerFallback(true, PROMO)
        assertTrue("\"fallback\"" in withFallback, withFallback)

        assertTrue("\"fallback\"" !in withFallback.withServerFallback(false, PROMO))
    }

    private companion object {
        const val PROMO = "promo_banner"
        const val BOX = "box"
        val LAYERS = EXAMPLES.single { "box" in it.body && "Layers" in it.name }.body
    }
}
