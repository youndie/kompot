package io.github.youndie.kompot.studio.diagnostics

import io.github.youndie.kompot.KompotDegradationKind
import io.github.youndie.kompot.studio.KompotStudioConfig
import io.github.youndie.kompot.studio.StudioRenderPane
import io.github.youndie.kompot.studio.toolkitRegistry
import ru.workinprogress.viddik.core.captureComposable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The four layers, and what each of them is for. The point of the panel is that they answer different
// questions about the same body: a body can be perfect JSON, of a type the profile forbids, with two
// nodes sharing an id, and still draw — three of those are invisible to the other three checks.
class DiagnosticsTest {
    private val config = KompotStudioConfig(registry = toolkitRegistry)

    @Test
    fun `one body, three layers, each pointing at its own node`() {
        val body =
            """
            {"type":"column","id":"root","children":[
              {"type":"text","id":"twice","text":"Hello world","spans":[{"text":"Hello there"}]},
              {"type":"text","id":"twice","text":"b"},
              {"type":"esim_transfer_widget","id":"widget"}
            ]}
            """.trimIndent()

        val findings = diagnose(config, body)
        val layers = findings.map { it.layer }.toSet()

        assertTrue("schema" in layers, "the type outside the profile was not reported: $findings")
        assertTrue("rules:component-id" in layers, "the duplicated id was not reported: $findings")
        assertTrue("rules:text-spans" in layers, "the drifted spans were not reported: $findings")

        // Each one points at its own node, in the notation the tree carries — which is what makes the
        // panel clickable without either side parsing the other.
        assertEquals("$.children[1]", findings.first { it.layer == "rules:component-id" }.path)
        assertEquals("$.children[0]", findings.first { it.layer == "rules:text-spans" }.path)
        assertEquals("$.children[2]", findings.first { it.layer == "schema" }.path)
    }

    @Test
    fun `a clean body produces nothing at all`() {
        // The control. Without it every assertion above is satisfied by a panel that reports
        // everything about everything.
        val body = """{"type":"column","id":"root","children":[{"type":"text","id":"t","text":"hi"}]}"""

        assertEquals(emptyList(), diagnose(config, body))
    }

    @Test
    fun `a body that is not JSON is reported once, with a line and a column`() {
        val body =
            """
            {"type":"column","id":"root",
             "children":[{"type":"text" "id":"t"}]}
            """.trimIndent()

        val findings = diagnose(config, body)

        // ONE finding, and only the syntax one: a validator handed a half-typed object reports the
        // absence of everything that was going to be typed next — a page of findings, none of them
        // the one that matters.
        assertEquals(1, findings.size, "a malformed body produced ${findings.size} findings")
        assertEquals("syntax", findings.single().layer)
        assertNull(findings.single().path, "a body with no tree was given a node to point at")
        assertTrue(
            Regex("line \\d+, column \\d+").containsMatchIn(findings.single().message),
            "the offset was not turned into a place: ${findings.single().message}",
        )
    }

    @Test
    fun `a root encoded by a concrete serialiser is named for what it is`() {
        val finding = degradationFinding(KompotDegradationKind.UNKNOWN_COMPONENT, "unknown")

        // The one mistake the whole "preview a body, not an object" design exists to catch, and it
        // reads as "unknown component" unless the hint says otherwise.
        assertEquals(Severity.WARNING, finding.severity)
        assertTrue("respondKompotComponent" in finding.message, "no hint about the root: ${finding.message}")
    }

    @Test
    fun `an ordinary degradation is a warning without the hint`() {
        val finding = degradationFinding(KompotDegradationKind.UNRENDERABLE_COMPONENT, "esim_transfer_widget")

        assertEquals(Severity.WARNING, finding.severity)
        // The negative half of the test above: the hint belongs to one case, and a hint on every
        // degradation would send everybody looking for a serialiser that is not the problem.
        assertTrue("respondKompotComponent" !in finding.message, "the hint leaked: ${finding.message}")
    }

    @Test
    fun `the render reports what it could not draw`() {
        val body =
            """{"type":"column","id":"root","children":[{"type":"esim_transfer_widget","id":"w"}]}"""
        val reported = mutableListOf<Finding>()

        captureComposable(width = 320, height = 320, compositionLocals = emptyList()) {
            StudioRenderPane(config = config, body = body, brand = null, dark = false) { kind, type ->
                val finding = degradationFinding(kind, type)
                if (reported.none { it.message == finding.message }) reported += finding
            }
        }

        // Layer 4 is the only one that answers "will THIS client draw it": the schema half already
        // said the type is outside the profile, and a deployment whose client simply lacks a renderer
        // for a type the profile DOES carry gets no schema finding at all.
        assertTrue(reported.isNotEmpty(), "the render drew an unfamiliar node without a word")
        assertTrue(reported.all { it.severity == Severity.WARNING })
    }
}
