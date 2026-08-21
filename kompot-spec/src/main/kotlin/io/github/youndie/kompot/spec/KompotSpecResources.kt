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
class KompotSpecResources(
    private val root: String,
    private val classLoader: ClassLoader = KompotSpecResources::class.java.classLoader,
) {
    private val schemaPath = "$root/schema"
    private val examplesPath = "$root/examples"

    private val json = Json { ignoreUnknownKeys = true }

    fun schemas(): Map<String, JsonObject> {
        val profile = readObject("$schemaPath/${KompotProtocol.PROFILE_FILE_NAME}")
        val modules = (profile.getValue("x-kompot-modules") as JsonArray).map { (it as JsonPrimitive).content }

        return modules.associate { module ->
            val fileName = KompotProtocol.fileNameFor(module)
            fileName to readObject("$schemaPath/$fileName")
        } + (KompotProtocol.PROFILE_FILE_NAME to profile)
    }

    fun openApi(): JsonObject = readObject("$schemaPath/${KompotProtocol.OPENAPI_FILE_NAME}")

    fun examplesIndex(): JsonObject = readObject("$examplesPath/${KompotProtocol.EXAMPLES_INDEX_FILE_NAME}")

    fun example(fileName: String): JsonElement = json.parseToJsonElement(read("$examplesPath/$fileName"))

    private fun readObject(path: String): JsonObject = json.parseToJsonElement(read(path)).jsonObject

    private fun read(path: String): String =
        classLoader
            .getResourceAsStream(path)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("No spec resource \"$path\" — was the module built without its schema/examples?")
}
