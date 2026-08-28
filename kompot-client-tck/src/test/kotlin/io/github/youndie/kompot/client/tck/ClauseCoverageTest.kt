package io.github.youndie.kompot.client.tck

import io.github.youndie.kompot.spec.KompotSpecResources
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// What the corpus does NOT hold a client to, written down where somebody can read it.
//
// Coverage by clause would have been cheap and would have overstated: §9.5 is one clause and six
// rules, and a single case under it looks like a covered paragraph. So §9 carries an id per rule and
// a case names the ones it holds — the report is then a fact rather than an impression.
//
// Nothing here fails for a rule with no case: the corpus is honest about being incomplete, and a
// guard that turns "not yet" into red teaches people to write a case that asserts nothing. What does
// fail is a case pointing at a rule that no longer exists, because that is how a report starts
// claiming coverage nobody has.
class ClauseCoverageTest {
    private val report = File("COVERAGE.md")

    // Read from the classpath rather than from ../kompot-spec/SPEC.md: the rules are shipped in the
    // artefact now, and reading them the way a consumer does is the only version of this that also
    // proves they arrived. The relative path was the same bytes only while somebody remembered to
    // package them.
    private val rules: Map<String, String> by lazy { KompotSpecResources(root = "kompot-spec").rules() }

    private val ruleIds: List<String> by lazy { rules.keys.filter { it.startsWith("9.") } }

    private fun cases() = ClientCorpusResources.cases()

    // Who holds a rule, for the rules this corpus does not. Without it the count lies in the other
    // direction: "18 of 33" reads as a half-finished corpus when most of the rest was never its job —
    // a server obligation, or something only pixels can answer.
    //
    // A rule missing from BOTH this map and the corpus is reported as unassigned rather than quietly
    // counted among the honest gaps, so a rule added to §9 tomorrow cannot slip past unnoticed.
    private val heldElsewhere =
        mapOf(
            "9.1.1" to "сервер: согласованность конверта",
            "9.1.2" to "сервер: форма ответа",
            "9.1.3" to "сервер: форма ответа",
            "9.2.1" to "сервер: `kompot-tck`, связность схемы",
            "9.2.2" to "сервер: `kompot-tck`, связность схемы",
            "9.2.3" to "сервер: `kompot-tck`, связность схемы",
            "9.2.4" to "сервер: `kompot-tck`, связность схемы",
            "9.5.2" to "сервер: тело ошибки",
            "9.6.4" to "сервер: патч против нового конверта",
            "9.6.5" to "отрисовка: тест рендерера `BoundReadOnlyFieldTest`, не корпус",
            "9.6.6" to "сервер: `kompot-tck`, проверка `patch`",
            "9.7.7" to "адаптер не принимает значения клиента при загрузке",
            "9.7.10" to "отрисовка: тесты `AmountInputRendererTest`, не корпус",
            "9.7.11" to "отрисовка: тест `VisualFormattingTest`, не корпус",
            "9.7.8" to "правило для плагина полей, на проводе не наблюдается",
            "9.7.9" to "отрисовка: снимки, а не корпус",
            "9.8.1" to "структура схемы, не решение клиента",
            "9.8.2" to "адаптер не умеет источники данных",
        )

    @Test
    fun `every rule a case names is a rule the specification has`() {
        assertTrue(ruleIds.isNotEmpty(), "no rule ids were found in §9 — this test proved nothing")

        val dangling =
            cases()
                .flatMap { case -> case.holds.map { case.id to it } }
                .filterNot { (_, rule) -> rule in ruleIds }

        assertEquals(emptyList(), dangling, "these cases name a rule §9 does not have")
    }

    @Test
    fun `the map of rules held elsewhere names rules the specification has`() {
        assertEquals(emptyList(), heldElsewhere.keys.filterNot { it in ruleIds }, "these are not rules of §9")
    }

    @Test
    fun `the coverage report says what the corpus holds and what it does not`() {
        val byRule = ruleIds.associateWith { rule -> cases().filter { rule in it.holds }.map { it.id } }
        val document =
            buildString {
                appendLine("# Покрытие §9")
                appendLine()
                appendLine("Сгенерировано `ClauseCoverageTest` по правилам §9 спеки и по случаям корпуса.")
                appendLine("Правило без случая — не дефект: корпус неполон и говорит об этом здесь, а не молчит.")
                appendLine()
                appendLine("| Правило | Случаи | Если не корпус — то кто |")
                appendLine("|---|---|---|")
                byRule.forEach { (rule, holders) ->
                    val who = if (holders.isNotEmpty()) "" else heldElsewhere[rule] ?: "**не назначено**"
                    appendLine("| `$rule` | ${if (holders.isEmpty()) "—" else holders.joinToString(", ") { "`$it`" }} | $who |")
                }
                appendLine()
                val covered = byRule.count { it.value.isNotEmpty() }
                val elsewhere = byRule.count { it.value.isEmpty() && it.key in heldElsewhere }
                appendLine("Корпус держит $covered из ${byRule.size}; ещё $elsewhere держит не он.")
                appendLine("Остальные — ${byRule.size - covered - elsewhere} — не держит никто.")
            }

        if (System.getenv("KOMPOT_SPEC_RECORD")?.equals("true", ignoreCase = true) == true) {
            report.writeText(document)
            return
        }

        assertEquals(
            document,
            report.takeIf { it.isFile }?.readText(),
            "the coverage report has drifted. Regenerate with KOMPOT_SPEC_RECORD=true ./gradlew :kompot-client-tck:test",
        )
    }
}
