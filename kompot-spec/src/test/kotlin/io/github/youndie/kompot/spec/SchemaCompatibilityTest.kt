package io.github.youndie.kompot.spec

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// One test per bullet of SPEC.md §15, plus the two questions a checker of this kind is usually wrong
// about: does it stay quiet when nothing changed, and does it stay quiet when something changed that
// it has no rule for. The first would make every run green for the wrong reason; the second is how a
// guard comes to cover the one shape it was written against.
//
// The synthetic fixture is two files — a module and a profile — because that is the smallest set in
// which the questions are answerable at all: whether a type degrades is stated by the module, while
// the closed list of types a build emits exists only in the profile.
class SchemaCompatibilityTest {
    @Test
    fun `the schema of the toolkit compared with itself reports nothing`() {
        assertEquals(emptyList(), SchemaCompatibility.compare(SchemaFiles.loadAll(), SchemaFiles.loadAll()))
    }

    // The positive control for the test above: the same real files, one field of one component made
    // required. Without it, "compared with itself reports nothing" would also pass if compare()
    // returned nothing at all.
    @Test
    fun `a field made required in the real schema is breaking`() {
        val before = SchemaFiles.loadAll()
        val after =
            before.edit("kompot-images.schema.json", "KompotComponentImage") { definition ->
                val required = definition.getValue("required").jsonArray + JsonPrimitive("scaleType")
                definition.with("required", JsonArray(required))
            }

        val change = SchemaCompatibility.compare(before, after).only()
        assertEquals(SchemaCompatibilityRules.REQUIRED_ADDED, change.rule)
        assertEquals(Compatibility.BREAKING, change.verdict)
        assertEquals("KompotComponentImage.scaleType", change.subject)
    }

    // Which FILE a definition is printed into is packaging, not protocol: the spec says a module's
    // schema does not depend on what is beside it. A checker keyed by file would call this a removal
    // and an addition — and a removal is the expensive verdict.
    @Test
    fun `a definition moved between files is not a change of the wire`() {
        val before = SchemaFiles.loadAll()
        val after = before.move("ImageScaleType", from = "kompot-images.schema.json", to = "kompot-core.schema.json")

        assertEquals(emptyList(), SchemaCompatibility.compare(before, after))
    }

    // THE BASE OF A HIERARCHY (B-63). Its fields are fields of every node, and the checker used to read
    // it for membership only: the two edits below passed as "nothing incompatible".
    @Test
    fun `a field taken out of required in the base of a hierarchy is breaking`() {
        val before = SchemaFiles.loadAll()
        val after =
            before.edit("kompot-core.schema.json", "KompotComponent") { definition ->
                definition.with("required", JsonArray(definition.getValue("required").jsonArray.filter { (it as JsonPrimitive).content != "id" }))
            }

        val change = SchemaCompatibility.compare(before, after).only()
        assertEquals(SchemaCompatibilityRules.REQUIRED_REMOVED, change.rule)
        assertEquals(Compatibility.BREAKING, change.verdict)
        assertEquals("KompotComponent.id", change.subject)
    }

    @Test
    fun `a field gone from the base of a hierarchy is reported`() {
        val before = SchemaFiles.loadAll()
        val after =
            before.edit("kompot-core.schema.json", "KompotComponent") { definition ->
                definition.with("properties", JsonObject(definition.getValue("properties").jsonObject - "modifiers"))
            }

        val change = SchemaCompatibility.compare(before, after).only()
        assertEquals(SchemaCompatibilityRules.FIELD_REMOVED, change.rule)
        assertEquals("KompotComponent.modifiers", change.subject)
    }

    // The direction B-49 took: a field with a default added to the base is compatible, and said so.
    @Test
    fun `an optional field added to the base of a hierarchy is compatible`() {
        val before = SchemaFiles.loadAll()
        val after =
            before.edit("kompot-core.schema.json", "KompotComponent") { definition ->
                val properties = definition.getValue("properties").jsonObject
                definition.with("properties", JsonObject(properties + ("tag" to JsonObject(mapOf("type" to JsonPrimitive("string"))))))
            }

        val change = SchemaCompatibility.compare(before, after).only()
        assertEquals(SchemaCompatibilityRules.FIELD_ADDED_OPTIONAL, change.rule)
        assertEquals(Compatibility.COMPATIBLE, change.verdict)
        assertEquals("KompotComponent.tag", change.subject)
    }

    @Test
    fun `nothing changed reports nothing`() {
        assertEquals(emptyList(), SchemaCompatibility.compare(schemaSet(), schemaSet()))
    }

    @Test
    fun `prose is not the wire`() {
        val after =
            schemaSet().edit("module.schema.json", "KompotComponentText") { definition ->
                definition.with("description", JsonPrimitive("A run of words, and now with a better sentence about it"))
            }

        assertEquals(emptyList(), SchemaCompatibility.compare(schemaSet(), after))
    }

    // §15, first list: a new type in a hierarchy with degradation.
    @Test
    fun `a new component type is compatible`() {
        val change = SchemaCompatibility.compare(schemaSet(), schemaSet(components = listOf("text", "badge"))).only()

        assertEquals(SchemaCompatibilityRules.TYPE_ADDED_DEGRADING, change.rule)
        assertEquals(Compatibility.COMPATIBLE, change.verdict)
        assertEquals("KompotComponent/badge", change.subject)
    }

    // §15, second list, and the one the diff of schema/*.json cannot tell from the case above: the
    // form hierarchies are open too, and have no fallback (§2.2).
    @Test
    fun `a new form field type is breaking`() {
        val after = schemaSet(fields = listOf("string_field", "slider"))
        val change = SchemaCompatibility.compare(schemaSet(), after).only()

        assertEquals(SchemaCompatibilityRules.TYPE_ADDED_NO_DEGRADATION, change.rule)
        assertEquals(Compatibility.BREAKING, change.verdict)
        assertEquals("FieldValue/slider", change.subject)
    }

    @Test
    fun `a new node of the closed modifier hierarchy is breaking`() {
        val change = SchemaCompatibility.compare(schemaSet(), schemaSet(modifiers = listOf("padding", "rotate"))).only()

        assertEquals(SchemaCompatibilityRules.CLOSED_HIERARCHY_EXTENDED, change.rule)
        assertEquals(Compatibility.BREAKING, change.verdict)
        assertEquals("KompotModifierNode/rotate", change.subject)
    }

    @Test
    fun `a removed type is breaking`() {
        val change = SchemaCompatibility.compare(schemaSet(components = listOf("text", "badge")), schemaSet()).only()

        assertEquals(SchemaCompatibilityRules.TYPE_REMOVED, change.rule)
        assertEquals(Compatibility.BREAKING, change.verdict)
        assertEquals("KompotComponent/badge", change.subject)
    }

    @Test
    fun `a new field outside required is compatible`() {
        val after = schemaSet(extraProperty = """"maxLines": {"type": ["integer", "null"]}""")
        val change = SchemaCompatibility.compare(schemaSet(), after).only()

        assertEquals(SchemaCompatibilityRules.FIELD_ADDED_OPTIONAL, change.rule)
        assertEquals(Compatibility.COMPATIBLE, change.verdict)
        assertEquals("KompotComponentText.maxLines", change.subject)
    }

    @Test
    fun `a new field inside required is breaking`() {
        val after =
            schemaSet(
                extraProperty = """"maxLines": {"type": "integer"}""",
                textRequired = listOf("type", "id", "text", "maxLines"),
            )
        val change = SchemaCompatibility.compare(schemaSet(), after).only()

        assertEquals(SchemaCompatibilityRules.REQUIRED_ADDED, change.rule)
        assertEquals(Compatibility.BREAKING, change.verdict)
    }

    @Test
    fun `a field leaving required is breaking`() {
        val change = SchemaCompatibility.compare(schemaSet(), schemaSet(textRequired = listOf("type", "id"))).only()

        assertEquals(SchemaCompatibilityRules.REQUIRED_REMOVED, change.rule)
        assertEquals(Compatibility.BREAKING, change.verdict)
        assertEquals("KompotComponentText.text", change.subject)
    }

    // §15 spells this one out — "a change of a field's type, narrowing nullable to non-nullable
    // included" — because it is the change that looks like a tightening rather than a break.
    @Test
    fun `nullable narrowed to non-nullable is breaking and says so`() {
        val before = schemaSet(textProperty = """{"type": ["string", "null"]}""")
        val change = SchemaCompatibility.compare(before, schemaSet()).only()

        assertEquals(SchemaCompatibilityRules.FIELD_TYPE_CHANGED, change.rule)
        assertEquals(Compatibility.BREAKING, change.verdict)
        assertTrue("nullable" in change.message, change.message)
    }

    @Test
    fun `a renamed discriminator is breaking`() {
        val change = SchemaCompatibility.compare(schemaSet(), schemaSet(discriminator = "kind")).only()

        assertEquals(SchemaCompatibilityRules.DISCRIMINATOR_RENAMED, change.rule)
        assertEquals(Compatibility.BREAKING, change.verdict)
    }

    // Not a bullet of §15 by itself, but a change of the type of every field that names the enum —
    // and the direction §14 explains: an unfamiliar constant fails the parse instead of falling back.
    @Test
    fun `a new enum value is breaking`() {
        val change = SchemaCompatibility.compare(schemaSet(), schemaSet(scale = listOf("crop", "fit", "tile"))).only()

        assertEquals(SchemaCompatibilityRules.FIELD_TYPE_CHANGED, change.rule)
        assertEquals(Compatibility.BREAKING, change.verdict)
        assertEquals("ImageScaleType/tile", change.subject)
    }

    @Test
    fun `closing an object to unknown fields is not classified by §15`() {
        val change = SchemaCompatibility.compare(schemaSet(), schemaSet(additionalProperties = false)).only()

        assertEquals(SchemaCompatibilityRules.UNKNOWN_FIELDS_CLOSED, change.rule)
        assertEquals(Compatibility.UNCLASSIFIED, change.verdict)
    }

    // THE TRIPWIRE. A keyword no rule here has ever met must come back as a question rather than as
    // silence — otherwise the first thing the generator learns to print is the first thing that
    // travels unchecked.
    @Test
    fun `a change no rule of §15 names comes back as a question`() {
        val after =
            schemaSet().edit("module.schema.json", "KompotComponentText") { definition ->
                definition.with("minProperties", JsonPrimitive(2))
            }

        val change = SchemaCompatibility.compare(schemaSet(), after).only()
        assertEquals(SchemaCompatibilityRules.UNCLASSIFIED_CHANGE, change.rule)
        assertEquals(Compatibility.UNCLASSIFIED, change.verdict)
        assertEquals("KompotComponentText", change.subject)
    }

    @Test
    fun `a new root keyword comes back as a question too`() {
        val after =
            schemaSet().let { set ->
                val module = set.getValue("module.schema.json").with("x-kompot-since", JsonPrimitive("0.38"))
                set + ("module.schema.json" to module)
            }

        val change = SchemaCompatibility.compare(schemaSet(), after).only()
        assertEquals(SchemaCompatibilityRules.UNCLASSIFIED_CHANGE, change.rule)
        assertEquals("module.schema.json", change.subject)
    }
}

// ---- the fixture: one module and the profile that closes it ----

private fun schemaSet(
    components: List<String> = listOf("text"),
    fields: List<String> = listOf("string_field"),
    modifiers: List<String> = listOf("padding"),
    textProperty: String = """{"type": "string"}""",
    extraProperty: String? = null,
    textRequired: List<String> = listOf("type", "id", "text"),
    additionalProperties: Boolean = true,
    // The discriminator of the CLOSED hierarchy, so that renaming it in a test is one change rather
    // than one per hierarchy.
    discriminator: String = "type",
    scale: List<String> = listOf("crop", "fit"),
): Map<String, JsonObject> {
    val mapping = { types: List<String>, target: String -> types.joinToString(", ") { """"$it": "#/@defs/$target"""" } }

    val module =
        """
        {
          "@schema": "https://json-schema.org/draft/2020-12/schema",
          "@id": "https://example.test/schema/module.schema.json",
          "x-kompot-contributes": {
            "KompotComponent": { ${mapping(components, "KompotComponentText")} },
            "FieldValue": { ${mapping(fields, "FieldValueString")} }
          },
          "@defs": {
            "KompotComponent": {
              "x-kompot-kind": "hierarchy", "x-kompot-open": true, "x-kompot-degrades": true, "type": "object"
            },
            "FieldValue": {
              "x-kompot-kind": "hierarchy", "x-kompot-open": true, "x-kompot-degrades": false, "type": "object"
            },
            "KompotModifierNode": {
              "x-kompot-kind": "hierarchy", "x-kompot-open": false,
              "oneOf": [ ${modifiers.joinToString(", ") { """{"@ref": "#/@defs/Modifier$it"}""" }} ],
              "discriminator": {
                "propertyName": "$discriminator",
                "mapping": { ${modifiers.joinToString(", ") { """"$it": "#/@defs/Modifier$it"""" }} }
              }
            },
            "ImageScaleType": {
              "x-kompot-kind": "enum", "type": "string", "enum": [ ${scale.joinToString(", ") { "\"$it\"" }} ]
            },
            "KompotComponentText": {
              "x-kompot-kind": "variant",
              "x-kompot-wire-type": "text",
              "type": "object",
              "properties": {
                "type": { "const": "text" },
                "id": { "type": "string" },
                "text": $textProperty${extraProperty?.let { ",\n                $it" }.orEmpty()}
              },
              "required": [ ${textRequired.joinToString(", ") { "\"$it\"" }} ],
              "additionalProperties": $additionalProperties
            },
            "FieldValueString": {
              "x-kompot-kind": "variant",
              "x-kompot-wire-type": "string_field",
              "type": "object",
              "properties": { "type": { "const": "string_field" }, "value": { "type": "string" } },
              "required": [ "type" ],
              "additionalProperties": true
            }
          }
        }
        """

    val profile =
        """
        {
          "@schema": "https://json-schema.org/draft/2020-12/schema",
          "@id": "https://example.test/schema/profile.schema.json",
          "@defs": {
            "KompotComponent": {
              "x-kompot-kind": "hierarchy", "x-kompot-open": false, "x-kompot-degrades": true,
              "discriminator": {
                "propertyName": "type",
                "mapping": { ${mapping(components, "KompotComponentText")} }
              }
            },
            "FieldValue": {
              "x-kompot-kind": "hierarchy", "x-kompot-open": false, "x-kompot-degrades": false,
              "discriminator": {
                "propertyName": "type",
                "mapping": { ${mapping(fields, "FieldValueString")} }
              }
            }
          }
        }
        """

    return mapOf("module.schema.json" to json(module), "profile.schema.json" to json(profile))
}

// `@` stands for the dollar of `$defs`, `$ref` and `$schema`: a raw Kotlin string would need
// `${'$'}` at every one of them, and the fixture is meant to be read as the JSON it is.
private fun json(text: String): JsonObject = Json.parseToJsonElement(text.replace('@', '$')).jsonObject

private fun List<SchemaChange>.only(): SchemaChange {
    assertEquals(1, size, "expected exactly one change, got:\n${joinToString("\n")}")
    return first()
}

private fun JsonObject.with(
    key: String,
    value: JsonElement,
): JsonObject = JsonObject(toMutableMap().also { it[key] = value })

private fun Map<String, JsonObject>.edit(
    file: String,
    definition: String,
    block: (JsonObject) -> JsonObject,
): Map<String, JsonObject> {
    val document = getValue(file)
    val definitions = document.getValue("\$defs").jsonObject.toMutableMap()
    definitions[definition] = block(definitions.getValue(definition).jsonObject)
    return this + (file to document.with("\$defs", JsonObject(definitions)))
}

private fun Map<String, JsonObject>.move(
    definition: String,
    from: String,
    to: String,
): Map<String, JsonObject> {
    val source = getValue(from).getValue("\$defs").jsonObject
    val moved = source.getValue(definition).jsonObject

    val without = getValue(from).with("\$defs", JsonObject(source.toMutableMap().also { it.remove(definition) }))
    val target = getValue(to).getValue("\$defs").jsonObject
    val with = getValue(to).with("\$defs", JsonObject(target.toMutableMap().also { it[definition] = moved }))

    return this + (from to without) + (to to with)
}
