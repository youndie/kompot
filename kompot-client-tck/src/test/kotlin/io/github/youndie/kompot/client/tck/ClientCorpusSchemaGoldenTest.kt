package io.github.youndie.kompot.client.tck

import io.github.youndie.kompot.spec.KompotSpec
import io.github.youndie.kompot.spec.KompotSpecModule
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

// The cases were the only description of their own format. A reader writing a runner had to infer the
// vocabulary of `expect` from the cases that happen to exist — and a key appearing in one case, in one
// shape, is exactly the one that gets inferred wrong: `noErrors` is a list of field ids, was read as a
// flag, and the case that carries it ran with nothing asserted while reporting green.
//
// So the format now ships a schema of its own, generated from the same types the runner decodes with,
// beside the cases in the artefact. It is generated rather than written because a hand-kept schema
// describes what its author remembered, and this file's whole purpose is to be believed by somebody
// who cannot read the Kotlin.
class ClientCorpusSchemaGoldenTest {
    private val file = File("corpus/client-corpus.schema.json")

    private val json = Json { prettyPrint = true; prettyPrintIndent = "  " }

    private fun generated(): JsonElement =
        KompotSpec
            .generateAll(
                listOf(
                    KompotSpecModule(
                        name = "client-corpus",
                        description =
                            "The format of a client conformance case: a form, the steps somebody takes on it, and " +
                                "what must be true afterwards. Not part of the wire — a client never sends or " +
                                "receives one of these.",
                        roots =
                            listOf(
                                ClientCorpusIndex.serializer().descriptor,
                                ClientCase.serializer().descriptor,
                            ),
                        // The steps carry their kind in "step" rather than "type", so that it does not
                        // read like the "type" of the field value inside the very same object.
                        discriminator = "step",
                        // The corpus is read by runners, not by clients: an unrecognised key here is a
                        // misread clause, not a newer sender being tolerated, so the schema closes the
                        // objects and a validating runner stops on `noErorrs` instead of skipping it.
                        openObjects = false,
                    ),
                ),
            ).single()
            .document

    @Test
    fun `the case-format schema equals what the generator prints from the types the runner uses`() {
        val document = json.encodeToString(JsonElement.serializer(), generated()) + "\n"

        if (System.getenv("KOMPOT_SPEC_RECORD")?.equals("true", ignoreCase = true) == true) {
            file.writeText(document)
            return
        }

        assertEquals(
            document,
            file.takeIf { it.isFile }?.readText(),
            "the case format has drifted from the schema shipped beside the cases. " +
                "Regenerate with KOMPOT_SPEC_RECORD=true ./gradlew :kompot-client-tck:test",
        )
    }
}
