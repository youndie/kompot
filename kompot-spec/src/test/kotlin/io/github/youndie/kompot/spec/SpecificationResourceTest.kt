package io.github.youndie.kompot.spec

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The specification travels in the jar, and its numbered rules are reachable from there. A
// conformance case names a rule by id; before this, the id pointed at a document living in one
// repository, so a reader on another language got the reference and no way to resolve it.
//
// Read the way a consumer reads it — from the classpath — rather than from the file beside the build
// script, because those are the same bytes only while somebody remembers to package them.
class SpecificationResourceTest {
    private val resources = KompotSpecResources(root = "kompot-spec")

    @Test
    fun `the specification travels in the artefact`() {
        val text = resources.specification()

        assertTrue(text.startsWith("# Спецификация протокола KOMPOT"), "the first line reads: ${text.lineSequence().first()}")
        assertTrue("## 9. Формы" in text)
    }

    @Test
    fun `every numbered rule resolves to the sentence that states it`() {
        val rules = resources.rules()

        assertTrue(rules.size >= 34, "only ${rules.size} rules were parsed")
        assertEquals(emptyList(), rules.filterValues { it.isBlank() }.keys.toList(), "these ids parsed to nothing")
        // The one this test exists for: a case names 9.4.3, and what it names has to be readable.
        assertTrue("перестаёт действовать вместе с полем" in rules.getValue("9.4.3"), rules.getValue("9.4.3"))
    }

    // A reference is not a statement: §9 mentions its own clauses constantly, and a parser that took
    // every backticked number would invent rules out of cross-references.
    @Test
    fun `an id the specification only refers to is not a rule`() {
        assertNull(resources.rule("16.5.1"))
        assertNull(resources.rule("9.4.99"))
    }
}
