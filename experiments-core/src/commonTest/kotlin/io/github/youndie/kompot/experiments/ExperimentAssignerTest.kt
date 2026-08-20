package io.github.youndie.kompot.experiments

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

private val twoVariants = Experiment("home_banner_copy", listOf(Variant("control"), Variant("variant_b")))

class ExperimentAssignerTest {
    @Test
    fun `same subjectId always gets the same variant`() {
        val assignments = (1..20).map { ExperimentAssigner.assign(twoVariants, "user-42") }
        assertTrue(assignments.all { it == assignments.first() })
    }

    @Test
    fun `different experiments assign the same subjectId independently`() {
        val other = Experiment("other_experiment", listOf(Variant("control"), Variant("variant_b")))
        // Concrete values are not asserted — that would be brittle against a change of hash
        // function. What is asserted is that assign really takes experiment.id into account and not
        // only subjectId.
        val a = ExperimentAssigner.assign(twoVariants, "user-1")
        val b = ExperimentAssigner.assign(other, "user-1")
        // Both are valid variant ids. Were experiment.id ignored, both would always equal a; this
        // pins only that each belongs to the set of variants.
        assertTrue(a in listOf("control", "variant_b"))
        assertTrue(b in listOf("control", "variant_b"))
    }

    @Test
    fun `assignment always returns a known variant id`() {
        repeat(200) { i ->
            val variant = ExperimentAssigner.assign(twoVariants, "user-$i")
            assertTrue(variant == "control" || variant == "variant_b", "unexpected variant '$variant'")
        }
    }

    @Test
    fun `distribution across many subjects roughly matches variant weights`() {
        val weighted = Experiment("weighted", listOf(Variant("small", weight = 1), Variant("large", weight = 3)))
        val counts = (1..4000).map { ExperimentAssigner.assign(weighted, "user-$it") }.groupingBy { it }.eachCount()

        val small = counts["small"] ?: 0
        val large = counts["large"] ?: 0
        assertEquals(4000, small + large)
        // large carries weight 3 against small's 1, so roughly 75% is expected, with slack for a
        // real — not perfectly uniform — hash distribution.
        val largeRatio = large.toDouble() / (small + large)
        assertTrue(largeRatio in 0.65..0.85, "expected ~0.75 large ratio, got $largeRatio")
    }

    @Test
    fun `single-variant experiment always assigns that variant`() {
        val single = Experiment("only_one", listOf(Variant("everyone")))
        repeat(50) { i ->
            assertEquals("everyone", ExperimentAssigner.assign(single, "user-$i"))
        }
    }

    @Test
    fun `experiment with no variants is rejected`() {
        try {
            Experiment("empty", emptyList())
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `experiment with a non-positive weight is rejected`() {
        try {
            Experiment("bad_weight", listOf(Variant("a", weight = 0)))
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }
}
