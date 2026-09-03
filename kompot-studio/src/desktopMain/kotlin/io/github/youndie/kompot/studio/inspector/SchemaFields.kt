package io.github.youndie.kompot.studio.inspector

import io.github.youndie.kompot.spec.KompotProtocol
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

// WHAT A NODE'S FIELDS ARE, read off the schema the build generates from its own types.
//
// For a backend developer the text IS the inspector. For anybody else — somebody who wants to change
// a spacing or a word — it is not, and the panel that replaces it needs to know, per property, its
// name, its JSON type, whether it is required, which values it allows, and what it means. The schema
// carries every one of those, and after B-06 the last one too.
//
// The alternative is a form built by reflecting over the SerialDescriptor, and it fails three ways at
// once: it cannot see the `x-kompot-*` markers, it has nothing at all to say about a type this build
// does not compile, and it re-derives what the schema already prints.
internal enum class FieldKind {
    STRING,
    NUMBER,
    BOOLEAN,

    // A closed set: a Kotlin enum on the wire, or a design-system key, or a word a deployment declared
    // the vocabulary of. Three sources, one editor — a list to pick from.
    CHOICE,

    // A nested polymorphic value: an action, mostly. Edited by the same machinery one level down.
    NESTED,

    // Anything the schema does not describe — a key of a type this build does not know, or a shape
    // with no editor. Shown as text rather than dropped: a property nobody can edit is still a
    // property somebody wrote.
    RAW,
}

internal data class PropertyField(
    val name: String,
    val kind: FieldKind,
    val required: Boolean,
    val description: String?,
    val options: List<String> = emptyList(),
    val pattern: String? = null,
    // For NESTED: the hierarchy whose members this value may be, so the editor can offer them.
    val hierarchy: String? = null,
)

// The definition key a wire type resolves to in this build's profile — "column" to
// "KompotComponentColumn". Everything else here works on definitions, so that an action's sub-form is
// the same code as a component's form.
internal fun defKeyFor(
    schemas: Map<String, JsonObject>,
    wireType: String,
    hierarchy: String = KompotProtocol.COMPONENT_HIERARCHY,
): String? {
    val profile = schemas[KompotProtocol.PROFILE_FILE_NAME] ?: return null
    val base = (profile["\$defs"] as? JsonObject)?.get(hierarchy)?.jsonObject ?: return null
    val mapping = (base["discriminator"] as? JsonObject)?.get("mapping")?.jsonObject ?: return null
    return (mapping[wireType] as? JsonPrimitive)?.content?.substringAfterLast('/')
}

internal fun fieldsFor(
    schemas: Map<String, JsonObject>,
    definitionKey: String,
    // The words a deployment declared for this type's open fields. The schema cannot carry them — the
    // protocol leaves those strings open on purpose — so they arrive from the configuration.
    words: Map<String, Set<String>> = emptyMap(),
    // What each kind of design-system key may be, from the brand kits.
    tokens: Map<String, List<String>> = emptyMap(),
): List<PropertyField> {
    val definition = findDefinition(schemas, definitionKey) ?: return emptyList()
    val properties = definition["properties"] as? JsonObject ?: return emptyList()
    val required =
        (definition["required"] as? JsonArray).orEmpty().map { (it as JsonPrimitive).content }.toSet()

    return properties.entries
        // The discriminator is not a field: changing it would make the node a different type, which is
        // the tree's business and not a text box's.
        .filter { (name, _) -> name != KompotProtocol.DISCRIMINATOR }
        .map { (name, schema) ->
            field(schemas, name, schema.jsonObject, name in required, words[name], tokens)
        }
}

private fun field(
    schemas: Map<String, JsonObject>,
    name: String,
    property: JsonObject,
    required: Boolean,
    declaredWords: Set<String>?,
    tokens: Map<String, List<String>>,
): PropertyField {
    // A nullable property is printed as anyOf(<the type>, null); the type is what an editor is for.
    val unwrapped =
        (property["anyOf"] as? JsonArray ?: property["oneOf"] as? JsonArray)
            ?.map { it.jsonObject }
            ?.firstOrNull { branch -> (branch["type"] as? JsonPrimitive)?.content != "null" }
            ?: property

    val description = (property["description"] as? JsonPrimitive)?.content
        ?: (unwrapped["description"] as? JsonPrimitive)?.content

    val referenced = (unwrapped["\$ref"] as? JsonPrimitive)?.content?.substringAfterLast('/')
    val target = referenced?.let { findDefinition(schemas, it) }

    // A word a deployment declared beats everything the schema could say about a plain string: the
    // schema calls it a string because the protocol leaves it open, and the set is the only thing that
    // knows better.
    if (declaredWords != null) {
        return PropertyField(name, FieldKind.CHOICE, required, description, declaredWords.sorted())
    }

    if (target != null) {
        val kind = (target["x-kompot-kind"] as? JsonPrimitive)?.content
        return when (kind) {
            "token" ->
                PropertyField(name, FieldKind.CHOICE, required, description, tokens[referenced].orEmpty())

            "enum" ->
                PropertyField(
                    name,
                    FieldKind.CHOICE,
                    required,
                    description,
                    (target["enum"] as? JsonArray).orEmpty().map { (it as JsonPrimitive).content },
                )

            "hierarchy" -> PropertyField(name, FieldKind.NESTED, required, description, hierarchy = referenced)
            else -> PropertyField(name, FieldKind.RAW, required, description)
        }
    }

    val enum = unwrapped["enum"] as? JsonArray
    if (enum != null) {
        return PropertyField(name, FieldKind.CHOICE, required, description, enum.map { (it as JsonPrimitive).content })
    }

    val pattern = (unwrapped["pattern"] as? JsonPrimitive)?.content
    val type = typeOf(unwrapped)

    return when (type) {
        "string" -> PropertyField(name, FieldKind.STRING, required, description, pattern = pattern)
        "integer", "number" -> PropertyField(name, FieldKind.NUMBER, required, description)
        "boolean" -> PropertyField(name, FieldKind.BOOLEAN, required, description)
        // An array, an object, anything else: text. The panel says what it is and lets somebody type
        // it rather than pretending the property is not there.
        else -> PropertyField(name, FieldKind.RAW, required, description)
    }
}

// `type` may be a name or a list of them ("integer" or ["integer", "null"]): the second is how a
// nullable primitive is printed, and reading only the first form calls half the properties untyped.
private fun typeOf(schema: JsonObject): String? =
    when (val declared = schema["type"]) {
        is JsonPrimitive -> declared.content
        is JsonArray -> declared.map { (it as JsonPrimitive).content }.firstOrNull { it != "null" }
        else -> null
    }

private fun findDefinition(
    schemas: Map<String, JsonObject>,
    key: String,
): JsonObject? =
    schemas.values.firstNotNullOfOrNull { document ->
        (document["\$defs"] as? JsonObject)?.get(key)?.jsonObject
    }

private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()
