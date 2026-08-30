package io.github.youndie.kompot.spec

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

// Reads a build's spec from the classpath. Inside the module that generates it the files sit right
// there on disk (see SchemaFiles), but every other consumer — a conformance kit, a harness on
// another stack, a published artefact — has nothing but the resources of a jar.
//
// The root is a parameter because the spec of a build lives under that build's own resource path:
// two spec jars on one classpath must not shadow each other's profile.
//
// No list of files is duplicated anywhere: module names come from the profile, example names from
// the corpus manifest.
public class KompotSpecResources(
    private val root: String,
    private val classLoader: ClassLoader = KompotSpecResources::class.java.classLoader,
) {
    private val schemaPath = "$root/schema"
    private val examplesPath = "$root/examples"

    private val json = Json { ignoreUnknownKeys = true }

    public fun schemas(): Map<String, JsonObject> {
        val profile = readObject("$schemaPath/${KompotProtocol.PROFILE_FILE_NAME}")
        val modules = (profile.getValue("x-kompot-modules") as JsonArray).map { (it as JsonPrimitive).content }

        return modules.associate { module ->
            val fileName = KompotProtocol.fileNameFor(module)
            fileName to readObject("$schemaPath/$fileName")
        } + (KompotProtocol.PROFILE_FILE_NAME to profile)
    }

    public fun openApi(): JsonObject = readObject("$schemaPath/${KompotProtocol.OPENAPI_FILE_NAME}")

    // The specification itself, as it travels in the artefact. Russian prose (see the readme): the
    // rules are the machine-readable part of it, and they are below.
    public fun specification(): String = read("$root/${KompotProtocol.SPEC_FILE_NAME}")

    // The numbered rules, by id: "9.4.3" to the sentence that states it. A conformance case names
    // ids, a finding can quote one, and a report can list the ones nothing holds — all of which need
    // the text to be reachable from the artefact rather than from somebody's checkout.
    //
    // Parsed from the blocks the specification marks as rules rather than from every backticked
    // number in it: §9 refers to its own clauses constantly, and a reference is not a statement.
    public fun rules(): Map<String, String> {
        val text = specification()
        val rules = LinkedHashMap<String, String>()

        RULE_BLOCK.findAll(text).forEach { block ->
            val body = block.groupValues[1].replace(Regex("\\s+"), " ").trim()
            val ids = RULE_ID.findAll(body).toList()
            ids.forEachIndexed { index, match ->
                val from = match.range.last + 1
                val to = if (index + 1 < ids.size) ids[index + 1].range.first else body.length
                rules[match.groupValues[1]] = body.substring(from, to).trim()
            }
        }
        return rules
    }

    public fun rule(id: String): String? = rules()[id]

    public fun examplesIndex(): JsonObject = readObject("$examplesPath/${KompotProtocol.EXAMPLES_INDEX_FILE_NAME}")

    public fun example(fileName: String): JsonElement = json.parseToJsonElement(read("$examplesPath/$fileName"))

    private companion object {
        // A rules paragraph: everything from the marker to the blank line that ends the paragraph.
        val RULE_BLOCK = Regex("""\*\*Правила\.\*\*(.+?)(?=\n\n)""", RegexOption.DOT_MATCHES_ALL)
        val RULE_ID = Regex("""`(\d+\.\d+\.\d+)`""")
    }

    private fun readObject(path: String): JsonObject = json.parseToJsonElement(read(path)).jsonObject

    private fun read(path: String): String =
        classLoader
            .getResourceAsStream(path)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("No spec resource \"$path\" — was the module built without its schema/examples?")
}
