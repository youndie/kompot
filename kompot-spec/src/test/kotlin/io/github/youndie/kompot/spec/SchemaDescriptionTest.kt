package io.github.youndie.kompot.spec

import io.github.youndie.kompot.registry.KompotComponentDoc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// WHAT A TYPE MEANS, carried from its KDoc to its schema.
//
// A SerialDescriptor has no comments in it, so until now a generated schema could only say that
// `maxLines` is a nullable integer. What it MEANS lived in the source and in SPEC.md, which is exactly
// where a second implementation on another stack cannot read it.
class SchemaDescriptionTest {
    private val documents: Map<String, JsonObject> =
        KompotSpec.generateAll(KompotToolkitSpec.modules).associate { it.fileName to it.document }

    private fun def(
        file: String,
        key: String,
    ): JsonObject = (documents.getValue(file)["\$defs"] as JsonObject)[key] as JsonObject

    private fun description(schema: JsonObject): String? = (schema["description"] as? JsonPrimitive)?.content

    @Test
    fun `a property's KDoc becomes its description`() {
        val text = def("kompot-standard.schema.json", "KompotComponentText")
        val properties = text["properties"] as JsonObject

        assertEquals(
            "How many lines the text may occupy before it is cut. Null lets it take as many as it needs.",
            description(properties["maxLines"] as JsonObject),
        )
        val column = def("kompot-standard.schema.json", "KompotComponentColumn")["properties"] as JsonObject
        assertEquals(
            "The gap between children, in density-independent pixels.",
            description(column["spacing"] as JsonObject),
        )
    }

    @Test
    fun `a type's own KDoc becomes the definition's description`() {
        assertEquals(
            "A run of words to show. The only node that carries copy, and every string a person reads is one.",
            description(def("kompot-standard.schema.json", "KompotComponentText")),
        )
    }

    @Test
    fun `a property nobody documented is printed exactly as before`() {
        // The control, and it is what makes this feature safe to adopt one sentence at a time: a type
        // with no KDoc must print the schema it printed yesterday, with no empty description and no
        // new key.
        val properties = def("kompot-standard.schema.json", "KompotComponentTable")["properties"] as JsonObject
        assertNull(description(properties["rows"] as JsonObject))
        assertNull(description(def("kompot-standard.schema.json", "KompotComponentTable")))
    }

    @Test
    fun `a hand-written annotation wins over a doc comment`() {
        // Both channels can describe one property, and the order is a decision rather than an
        // accident: an annotation is written INTO a spec module deliberately, usually to state a
        // format the Kotlin type cannot. A KDoc must not quietly replace one.
        val module =
            KompotSpecModule(
                name = "test-precedence",
                description = "two descriptions of one property",
                roots = listOf(Documented.serializer().descriptor),
                docs = mapOf("documented" to KompotComponentDoc(properties = mapOf("value" to "from the KDoc"))),
                annotations =
                    mapOf(
                        "Documented" to
                            mapOf("value" to buildJsonObject { put("description", "from the spec module") }),
                    ),
            )

        val generated = KompotSpec.generateAll(listOf(module)).single().document
        val properties = ((generated["\$defs"] as JsonObject)["Documented"] as JsonObject)["properties"] as JsonObject

        assertEquals("from the spec module", description(properties["value"] as JsonObject))
    }
}

@Serializable
@SerialName("documented")
private data class Documented(
    val value: String,
)
