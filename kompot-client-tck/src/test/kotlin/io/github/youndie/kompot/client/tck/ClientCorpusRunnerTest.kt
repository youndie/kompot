package io.github.youndie.kompot.client.tck

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// The runner held to the property the corpus exists for: a case that checks nothing must not be able
// to report success, and a case a runner does not fully understand must stop it rather than shrink.
//
// Both failures are invisible from the outside — the report is green either way — so they are checked
// here rather than trusted.
class ClientCorpusRunnerTest {
    private class SilentClient : KompotFormClient {
        override fun load(form: kotlinx.serialization.json.JsonObject) = Unit

        override fun set(
            fieldId: String,
            value: kotlinx.serialization.json.JsonObject,
        ) = Unit

        override fun blur(fieldId: String) = Unit

        override fun applyPatch(patch: kotlinx.serialization.json.JsonObject) = Unit

        override fun submit() = Unit

        override fun visibleFields(): List<String> = emptyList()

        override fun errors(): Map<String, String> = emptyMap()

        override fun payload(): kotlinx.serialization.json.JsonObject? = null
    }

    private val form =
        buildJsonObject {
            put("formId", "f")
            put("fields", kotlinx.serialization.json.buildJsonArray { })
        }

    private fun case(expect: ClientExpectation) =
        ClientCase(
            id = "case",
            clause = "§9",
            title = "a case",
            why = "so a failure names a rule",
            form = form,
            expect = expect,
        )

    @Test
    fun `a case whose expectation asserts nothing is a finding`() {
        val report = ClientCorpusRunner(listOf(case(ClientExpectation()))) { FormControllerAdapter() }.run()

        assertEquals(1, report.findings.size, report.toString())
        assertContains(report.findings.single().message, "asserted nothing")
    }

    // The shape the report arrived as: a clause present but empty. It reads as an expectation and
    // checks nobody.
    @Test
    fun `an expectation naming no subject counts as nothing`() {
        val report =
            ClientCorpusRunner(listOf(case(ClientExpectation(noErrors = emptyList(), errors = emptyMap())))) {
                FormControllerAdapter()
            }.run()

        assertEquals(1, report.findings.size, report.toString())
        assertContains(report.findings.single().message, "asserted nothing")
    }

    @Test
    fun `a case that does assert something is clean`() {
        val report = ClientCorpusRunner(listOf(case(ClientExpectation(visibleFields = emptyList())))) { FormControllerAdapter() }.run()

        assertTrue(report.isClean, report.toString())
    }

    // The failure mode of the report itself: `noErrors` was read as a flag, the comparison never held,
    // and the case ran with one clause fewer while reporting green. A runner that does not know a key
    // must say so.
    // An adapter that cannot answer a question has not answered it. Reporting the case as passed
    // would credit a client for a rule nobody checked; reporting it as failed would accuse it of
    // sending nothing. Both are lies, in opposite directions.
    @Test
    fun `a case the adapter cannot answer is neither passed nor failed`() {
        val expectation = ClientExpectation(requests = listOf(buildJsonObject { put("kind", "patch") }))

        // An adapter written before the operation existed: it inherits the default, which records
        // nothing — exactly the compatibility that default is for.
        val report = ClientCorpusRunner(listOf(case(expectation))) { SilentClient() }.run()

        assertTrue(report.isClean, report.toString())
        assertEquals(1, report.unchecked.size, report.toString())
        assertContains(report.unchecked.single().message, "does not record")
    }

    @Test
    fun `an expectation key the runner does not know stops the corpus`() {
        val text =
            """
            {
              "id": "c", "clause": "§9", "title": "t", "why": "w",
              "form": {"formId": "f", "fields": []},
              "expect": {"noErorrs": ["code"]}
            }
            """.trimIndent()

        assertFailsWith<SerializationException> {
            ClientCorpusRunner.casesFrom(index = """{"cases":["c.json"]}""", read = { text })
        }
    }

    @Test
    fun `a step the runner does not know stops the corpus`() {
        val text =
            """
            {
              "id": "c", "clause": "§9", "title": "t", "why": "w",
              "form": {"formId": "f", "fields": []},
              "steps": [{"step": "focus", "fieldId": "code"}],
              "expect": {"visibleFields": []}
            }
            """.trimIndent()

        assertFailsWith<SerializationException> {
            ClientCorpusRunner.casesFrom(index = """{"cases":["c.json"]}""", read = { text })
        }
    }
}
