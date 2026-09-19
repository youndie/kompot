package io.github.youndie.kompot.spec

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

// THE CHECK AS CI RUNS IT: the schema of the working tree against the schema of the revision this
// branch was cut from, classified by SchemaCompatibility and held against the one escape hatch §15
// itself provides.
//
// THE ESCAPE HATCH IS §13, and it is not a file of exceptions. §15 forbids an incompatible change
// "without a change of the protocol version", and §13 is the journal an implementation on another
// stack reads when it updates: so a run with breaking findings passes exactly when the same change
// adds an entry to that journal. Nothing is left behind to go stale — the alternative, a list of
// waived findings, would turn red in the NEXT pull request, in somebody else's hands, once the waived
// change had landed on main and stopped being a difference at all.
//
// It shells out to git rather than taking two directories, because the question is always asked of a
// revision ("what does this branch do to the schema?") and a step that has to materialise the base
// first is a step somebody runs against the wrong tree.
public fun main(args: Array<String>) {
    val options = options(args)
    val git = Git(File(".").absoluteFile)

    val head =
        git.resolve("HEAD")
            ?: fail("not a git repository, or HEAD does not resolve — there is nothing to compare against")
    val base =
        git.resolve(options.base)
            ?: fail(
                "the base revision \"${options.base}\" does not resolve. In CI pass the base of the pull request; " +
                    "locally fetch it first (git fetch origin main).",
            )
    val mergeBase =
        git.run("merge-base", head, base)
            ?: fail(
                "no merge base between HEAD and \"${options.base}\". A shallow clone is the usual cause: " +
                    "actions/checkout needs fetch-depth: 0 for this check.",
            )

    println("Schema compatibility (SPEC.md §15): ${options.schemaDirectory} against $mergeBase (${options.base})")

    if (mergeBase == head) {
        // Said out loud rather than passed over: against this revision only what is not committed yet
        // can show up, and "no incompatible changes" out of a run that had almost nothing to compare
        // is the kind of pass that gets believed. It is the normal answer locally (the working tree
        // against HEAD) and a misconfigured base in CI, and the line tells the two apart.
        println(
            "The base IS this revision: only changes to ${options.schemaDirectory} that are not committed yet " +
                "can show up here.",
        )
    }

    val prefix = git.run("rev-parse", "--show-prefix").orEmpty()
    val before = git.schemaAt(mergeBase, prefix + options.schemaDirectory)
    if (before.isEmpty()) {
        fail(
            "no schema files at $mergeBase:$prefix${options.schemaDirectory} — the baseline is missing, and an empty " +
                "baseline would report every type of the protocol as new. Check --base and --schema.",
        )
    }

    val after = File(options.schemaDirectory).listJson()
    if (after.isEmpty()) fail("no schema files in ${File(options.schemaDirectory).absolutePath}")

    val changes = SchemaCompatibility.compare(before, after)
    println("${before.size} files against ${after.size}, ${changes.size} changes")

    report(changes)

    val blocking = changes.filter { it.verdict != Compatibility.COMPATIBLE }
    if (blocking.isEmpty()) {
        println("\nNothing incompatible. The protocol version stands.")
        return
    }

    val declared =
        git.journalEntriesAdded(
            revision = mergeBase,
            repositoryPath = prefix + options.specFile,
            local = File(options.specFile),
            section = options.journalSection,
        )
    if (declared.isEmpty()) {
        println(
            "\n${blocking.size} change(s) §15 does not allow without a change of the protocol version, and " +
                "${options.specFile} §${options.journalSection} gained no entry in this revision.\n" +
                "\nEither make the change compatible, or declare it: add a §${options.journalSection}.N section to " +
                "${options.specFile} saying what was, what is and whom it touches — that journal is what an " +
                "implementation on another stack reads when it updates, and it is the one thing §15 asks for in " +
                "exchange.\n" +
                "\nA finding marked ${Compatibility.UNCLASSIFIED} is not a verdict: §15 has no rule for it. Add the " +
                "rule to SchemaCompatibility if the change is safe, declare it if it is not.",
        )
        exitProcess(1)
    }

    println(
        "\n${blocking.size} incompatible change(s), declared in ${options.specFile} " +
            "§${options.journalSection}: ${declared.joinToString("; ")}",
    )
}

private fun report(changes: List<SchemaChange>) {
    Compatibility.entries.forEach { verdict ->
        val group = changes.filter { it.verdict == verdict }
        if (group.isEmpty()) return@forEach
        println("\n$verdict (${group.size}):")
        group.forEach { change -> println("  ${change.subject}  [${change.rule}]\n      ${change.message}") }
    }
}

private class Options(
    val base: String,
    val schemaDirectory: String,
    val specFile: String,
    val journalSection: String,
)

private fun options(args: Array<String>): Options {
    val named =
        args.toList().chunked(2).associate { pair ->
            val name = pair.first().removePrefix("--")
            name to (pair.getOrNull(1) ?: fail("--$name has no value"))
        }

    val unknown = named.keys - setOf("base", "schema", "spec", "journal")
    if (unknown.isNotEmpty()) fail("unknown option(s): ${unknown.joinToString()}")

    return Options(
        // The branch this one was cut from. CI passes the base of the pull request, and on a push to
        // main the previous head — both are revisions, not the moving tip of a branch, so a run
        // cannot be made green by somebody else's merge. An empty value, or the all-zero sha a push
        // event carries when there is no previous head, is no revision at all rather than an error
        // worth stopping a build over.
        base = named["base"]?.takeIf { it.isNotBlank() && it.any { character -> character != '0' } } ?: "origin/main",
        schemaDirectory = named["schema"] ?: SchemaFiles.directory.path,
        specFile = named["spec"] ?: KompotProtocol.SPEC_FILE_NAME,
        journalSection = named["journal"] ?: "13",
    )
}

private class Git(private val directory: File) {
    fun resolve(revision: String): String? = run("rev-parse", "--verify", "--quiet", "$revision^{commit}")

    // The names in <revision>:<path>, bare. `--full-tree` because this runs from the module's
    // directory and ls-tree otherwise limits what it lists to the path the caller stands in — the
    // tree being asked about IS that path, so the answer came back empty and every type of the
    // protocol would have read as new.
    fun schemaAt(
        revision: String,
        path: String,
    ): Map<String, JsonObject> {
        val names =
            run("ls-tree", "--full-tree", "--name-only", "$revision:$path")
                ?.lines()
                .orEmpty()
                .filter { it.endsWith(".json") }
        return names.associateWith { name ->
            val text = run("show", "$revision:$path/$name") ?: fail("cannot read $revision:$path/$name")
            Json.parseToJsonElement(text) as JsonObject
        }
    }

    // The subsections the journal GAINED — new ids, not a longer file: editing the prose of an entry
    // that is already there says nothing about this change.
    fun journalEntriesAdded(
        revision: String,
        repositoryPath: String,
        local: File,
        section: String,
    ): List<String> {
        val before = journalEntries(run("show", "$revision:$repositoryPath").orEmpty(), section)
        val after = journalEntries(local.takeIf { it.isFile }?.readText().orEmpty(), section)
        return (after.keys - before.keys).sorted().map { id -> "§$id ${after.getValue(id)}" }
    }

    private fun journalEntries(
        markdown: String,
        section: String,
    ): Map<String, String> =
        markdown
            .lineSequence()
            .mapNotNull { line -> Regex("^###\\s+($section\\.\\d+)\\.?\\s*(.*)$").find(line) }
            .associate { match -> match.groupValues[1] to match.groupValues[2].trim() }

    fun run(vararg command: String): String? {
        val process =
            ProcessBuilder(listOf("git") + command)
                .directory(directory)
                .redirectErrorStream(false)
                .start()

        val output = process.inputStream.bufferedReader().readText()
        val errors = process.errorStream.bufferedReader().readText()
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroy()
            fail("git ${command.joinToString(" ")} did not finish in a minute")
        }
        // A non-zero exit is an answer here ("this revision has no such path"), not a crash — every
        // caller that cannot go on without the output says so itself.
        if (process.exitValue() != 0) {
            if (errors.isNotBlank() && command.firstOrNull() != "rev-parse") {
                println("  git ${command.joinToString(" ")}: ${errors.trim().lines().first()}")
            }
            return null
        }
        return output.trim().takeIf { it.isNotEmpty() }
    }
}

private fun File.listJson(): Map<String, JsonObject> =
    (listFiles { file -> file.name.endsWith(".json") } ?: emptyArray())
        .sortedBy { it.name }
        .associate { file -> file.name to (Json.parseToJsonElement(file.readText()) as JsonObject) }

private fun fail(message: String): Nothing {
    System.err.println("Schema compatibility: $message")
    exitProcess(2)
}
