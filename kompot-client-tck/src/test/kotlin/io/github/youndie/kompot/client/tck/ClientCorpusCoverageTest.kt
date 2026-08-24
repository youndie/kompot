package io.github.youndie.kompot.client.tck

import io.github.youndie.kompot.form.standard.formStandardSerializersModule
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.modules.SerializersModuleCollector
import kotlinx.serialization.descriptors.SerialDescriptor
import java.io.File
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// What the corpus does NOT hold a client to. A rule with no case passes for reasons nobody checked:
// an implementation can leave it out entirely, run the corpus, and be told it conforms.
//
// The list to compare against is not written by hand — it is every type registered for the wire, read
// out of the serializers module. A hand-kept list would go stale the first time somebody adds a rule,
// which is precisely the moment this test exists for.
@OptIn(ExperimentalSerializationApi::class)
class ClientCorpusCoverageTest {
    private val corpus = File("corpus")

    private val index =
        ClientCorpusRunner.json.decodeFromString(ClientCorpusIndex.serializer(), File(corpus, "index.json").readText())

    // Everything in the directory that is a case: the index and the format schema describe the corpus
    // rather than belonging to it, and both are named by the index rather than by a rule written here,
    // so renaming one cannot leave this test quietly excluding a file that is a case.
    private fun caseFiles(): List<File> =
        (corpus.listFiles { file -> file.name.endsWith(".json") && file.name !in setOf("index.json", index.schema) } ?: emptyArray())
            .sortedBy { it.name }

    private fun registeredNames(): Set<String> {
        val names = mutableSetOf<String>()
        formStandardSerializersModule.dumpTo(
            object : SerializersModuleCollector {
                override fun <T : Any> contextual(
                    kClass: KClass<T>,
                    provider: (typeArgumentsSerializers: List<KSerializer<*>>) -> KSerializer<*>,
                ) = Unit

                override fun <Base : Any, Sub : Base> polymorphic(
                    baseClass: KClass<Base>,
                    actualClass: KClass<Sub>,
                    actualSerializer: KSerializer<Sub>,
                ) {
                    names += actualSerializer.descriptor.serialName
                }

                override fun <Base : Any> polymorphicDefaultDeserializer(
                    baseClass: KClass<Base>,
                    defaultDeserializerProvider: (className: String?) -> kotlinx.serialization.DeserializationStrategy<Base>?,
                ) = Unit

                override fun <Base : Any> polymorphicDefaultSerializer(
                    baseClass: KClass<Base>,
                    defaultSerializerProvider: (value: Base) -> kotlinx.serialization.SerializationStrategy<Base>?,
                ) = Unit
            },
        )
        return names
    }

    private fun typeNamesIn(element: JsonElement): Set<String> =
        when (element) {
            is JsonObject ->
                element.entries.flatMapTo(mutableSetOf()) { (key, value) ->
                    val own = if (key == "type" && value is JsonPrimitive && value.isString) setOf(value.content) else emptySet()
                    own + typeNamesIn(value)
                }
            is JsonArray -> element.flatMapTo(mutableSetOf()) { typeNamesIn(it) }
            else -> emptySet()
        }

    // A case file nobody listed is a case nobody runs, and it looks exactly like one that passes.
    @Test
    fun `the index lists every case file and no others`() {
        val onDisk = caseFiles().map { it.name }.sorted()
        val listed = index.cases.sorted()

        assertTrue(onDisk.isNotEmpty(), "no case files were found beside index.json — this test proved nothing")
        assertEquals(onDisk, listed)
    }

    @Test
    fun `every type that can travel in a form is used by at least one case`() {
        val registered = registeredNames()
        val used = caseFiles().flatMapTo(mutableSetOf()) { file -> typeNamesIn(Json.parseToJsonElement(file.readText())) }

        assertTrue(registered.isNotEmpty(), "no registered types were found — this test proved nothing")

        val uncovered = (registered - used).sorted()
        assertTrue(
            uncovered.isEmpty(),
            "these can travel on the wire and no case exercises them, so an implementation may omit them and still " +
                "pass the corpus: $uncovered",
        )
    }
}
