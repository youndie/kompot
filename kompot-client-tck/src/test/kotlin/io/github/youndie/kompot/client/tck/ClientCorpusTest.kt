package io.github.youndie.kompot.client.tck

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

// The toolkit's own client, held to the corpus. Nothing has ever asked it whether it agrees with §9 —
// its tests were written by the same hand as the code, so they check the author's reading twice.
class ClientCorpusTest {
    private val corpus = File("corpus")

    @Test
    fun `form-core answers the corpus`() {
        val cases =
            ClientCorpusRunner.casesFrom(
                index = File(corpus, "index.json").readText(),
                read = { name -> File(corpus, name).readText() },
            )

        val report = ClientCorpusRunner(cases) { FormControllerAdapter() }.run()

        // Not vacuous by accident: a corpus that loaded nothing would report a clean run of zero.
        assertTrue(report.casesRun >= 12, "only ${report.casesRun} cases were loaded")
        assertTrue(report.isClean, report.toString())
    }
}
