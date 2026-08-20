package io.github.youndie.kompot.spec

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.io.File

// The schema golden files. They live in the module's schema/ directory and are committed: it is they,
// not the generator's code, that another team reads and that shows up in the diff of a pull request.
// The same trick as screenshot goldens: generated, but pinned and compared in CI.
@OptIn(ExperimentalSerializationApi::class)
object SchemaFiles {
    // The working directory of a Gradle Test task is the module's directory, hence a relative path.
    val directory: File = File("schema")

    // An environment variable rather than a system property, so that recording needs no build-file
    // plumbing to pass it through.
    const val RECORD_ENV = "KOMPOT_SPEC_RECORD"

    private val json =
        Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }

    val recordMode: Boolean get() = System.getenv(RECORD_ENV)?.equals("true", ignoreCase = true) == true

    fun render(document: JsonObject): String = json.encodeToString(JsonObject.serializer(), document) + "\n"

    fun read(fileName: String): String? = File(directory, fileName).takeIf { it.isFile }?.readText()

    fun parse(fileName: String): JsonObject =
        json.decodeFromString(
            JsonObject.serializer(),
            read(fileName) ?: error("No schema file $fileName — regenerate with $RECORD_ENV=true"),
        )

    fun write(
        fileName: String,
        document: JsonObject,
    ) {
        directory.mkdirs()
        File(directory, fileName).writeText(render(document))
    }

    // Every file in the directory: module schemas, the profile and the OpenAPI document. The validator
    // and the reference check need all of them, because $refs travel between all three kinds.
    fun loadAll(): Map<String, JsonObject> =
        names().associateWith { name -> json.decodeFromString(JsonObject.serializer(), File(directory, name).readText()) }

    fun names(): List<String> = (directory.listFiles { file -> file.name.endsWith(".json") } ?: emptyArray()).map { it.name }.sorted()
}

// The corpus of reference JSON bodies. A directory of its own rather than a subfolder of schema/: a
// schema describes the shape of data, the corpus shows concrete data, and the two are consumed
// differently — one generates classes, the other drives tests.
@OptIn(ExperimentalSerializationApi::class)
object ExampleFiles {
    val directory: File = File("examples")

    private val json =
        Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }

    fun render(document: JsonElement): String = json.encodeToString(JsonElement.serializer(), document) + "\n"

    fun read(fileName: String): String? = File(directory, fileName).takeIf { it.isFile }?.readText()

    fun write(
        fileName: String,
        document: JsonElement,
    ) {
        directory.mkdirs()
        File(directory, fileName).writeText(render(document))
    }

    fun parse(fileName: String): JsonElement =
        json.parseToJsonElement(read(fileName) ?: error("No example file $fileName — regenerate with ${SchemaFiles.RECORD_ENV}=true"))

    fun names(): List<String> = (directory.listFiles { file -> file.name.endsWith(".json") } ?: emptyArray()).map { it.name }.sorted()
}
