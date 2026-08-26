package io.github.youndie.kompot.client.tck

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The toolkit's own client, held to the corpus. Nothing had ever asked it whether it agrees with §9 —
// its tests were written by the same hand as the code, so they check the author's reading twice.
//
// Loaded the way a CONSUMER loads it, from the classpath rather than from a directory beside the
// build file. Reading the files directly would have passed while the published artefact carried no
// cases at all, which is exactly what it did.
class ClientCorpusTest {
    @Test
    fun `form-core answers the corpus`() {
        val cases = ClientCorpusResources.cases()

        val report = ClientCorpusRunner(cases) { FormControllerAdapter() }.run()

        // Not vacuous by accident: a corpus that loaded nothing would report a clean run of zero.
        assertTrue(report.casesRun >= 12, "only ${report.casesRun} cases were loaded")
        assertTrue(report.isClean, report.toString())
        // And nothing was skipped for want of an operation: this adapter is the one that has to
        // answer every question the corpus asks, or the corpus grows a clause nothing here holds.
        assertEquals(emptyList(), report.unchecked, report.toString())
    }
}
