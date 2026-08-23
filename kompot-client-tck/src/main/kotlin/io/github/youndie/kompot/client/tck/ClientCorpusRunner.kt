package io.github.youndie.kompot.client.tck

import kotlinx.serialization.json.Json

data class ClientFinding(
    val case: String,
    val clause: String,
    val message: String,
) {
    override fun toString() = "[$clause] $case — $message"
}

data class ClientReport(
    val findings: List<ClientFinding>,
    val casesRun: Int,
) {
    val isClean: Boolean get() = findings.isEmpty()

    override fun toString(): String =
        if (isClean) {
            "Client corpus: $casesRun cases, no violations"
        } else {
            "Client corpus: ${findings.size} violations out of $casesRun cases\n" + findings.joinToString("\n")
        }
}

// Drives a client through the corpus. It knows nothing about how a client is built — only the seven
// operations of KompotFormClient — and nothing about a domain: every case is written in the toolkit's
// own vocabulary, so this ships with the protocol rather than with an application.
class ClientCorpusRunner(
    private val cases: List<ClientCase>,
    // A fresh client per case: state left behind by one case turning the next one green (or red) is
    // the classic way a corpus stops meaning anything.
    private val clientFactory: () -> KompotFormClient,
) {
    fun run(): ClientReport {
        val findings = cases.flatMap { case -> runCase(case) }
        return ClientReport(findings, cases.size)
    }

    private fun runCase(case: ClientCase): List<ClientFinding> {
        val client = clientFactory()
        val findings = mutableListOf<ClientFinding>()

        runCatching {
            client.load(case.form)
            case.steps.forEach { step ->
                when (step) {
                    is ClientStep.Set -> client.set(step.fieldId, step.value)
                    is ClientStep.Blur -> client.blur(step.fieldId)
                    is ClientStep.Patch -> client.applyPatch(step.patch)
                    ClientStep.Submit -> client.submit()
                }
            }
        }.onFailure { failure ->
            return listOf(ClientFinding(case.id, case.clause, "the case could not be run: $failure"))
        }

        case.expect.visibleFields?.let { expected ->
            val actual = client.visibleFields().sorted()
            if (actual != expected.sorted()) {
                findings += ClientFinding(case.id, case.clause, "visible fields are $actual, expected ${expected.sorted()} — ${case.why}")
            }
        }

        case.expect.payloadBlocked?.let { blocked ->
            val payload = client.payload()
            if (blocked && payload != null) {
                findings += ClientFinding(case.id, case.clause, "the submit was allowed with $payload — ${case.why}")
            }
            if (!blocked && payload == null) {
                findings += ClientFinding(case.id, case.clause, "the submit was blocked — ${case.why}")
            }
        }

        case.expect.payload?.let { expected ->
            when (val actual = client.payload()) {
                null -> findings += ClientFinding(case.id, case.clause, "the submit was blocked, expected $expected — ${case.why}")
                else ->
                    if (actual != expected) {
                        findings += ClientFinding(case.id, case.clause, "the payload is $actual, expected $expected — ${case.why}")
                    }
            }
        }

        case.expect.errors?.forEach { (fieldId, message) ->
            val actual = client.errors()[fieldId]
            if (actual != message) {
                findings += ClientFinding(case.id, case.clause, "the error on \"$fieldId\" is ${actual ?: "absent"}, expected \"$message\" — ${case.why}")
            }
        }

        case.expect.noErrors?.forEach { fieldId ->
            client.errors()[fieldId]?.let { message ->
                findings += ClientFinding(case.id, case.clause, "\"$fieldId\" carries the error \"$message\" and should carry none — ${case.why}")
            }
        }

        return findings
    }

    companion object {
        val json = Json { ignoreUnknownKeys = true; classDiscriminator = "step" }

        fun casesFrom(
            index: String,
            read: (String) -> String,
        ): List<ClientCase> =
            json
                .decodeFromString(ClientCorpusIndex.serializer(), index)
                .cases
                .map { json.decodeFromString(ClientCase.serializer(), read(it)) }
    }
}
