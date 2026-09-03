package io.github.youndie.kompot.spec

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

// EVERY DESIGN-SYSTEM KEY A BODY NAMES, and where it names it.
//
// A token is deliberately an open string: a server names a role and the client's design system
// decides what it looks like, so an unfamiliar one falls back rather than taking the screen down.
// That openness has a price nobody was paying — a token no kit names draws in the built-in palette,
// which is one control in one state in Material's purple inside somebody's brand, and the person who
// finds it is a customer with a screenshot.
//
// Finding them needs the SCHEMA, not a guess: `style` and `color` are ordinary strings in the JSON,
// and the only thing that says one is a token is the property's $ref. So this walks the body ALONGSIDE
// the schema, the way the validator does, and collects what it lands on.
public data class TokenUse(
    val path: JsonPath,
    // The definition the property refers to: ColorToken, TypographyToken.
    val kind: String,
    val value: String,
)

public fun tokenUses(
    body: JsonElement,
    schemas: Map<String, JsonObject>,
    ref: String = "${KompotProtocol.PROFILE_FILE_NAME}#/\$defs/${KompotProtocol.COMPONENT_HIERARCHY}",
): List<TokenUse> {
    val uses = mutableListOf<TokenUse>()
    TokenWalk(schemas, uses).walk(body, ref, currentFile = "", path = JsonPath.ROOT)
    return uses
}

private class TokenWalk(
    private val schemas: Map<String, JsonObject>,
    private val uses: MutableList<TokenUse>,
) {
    fun walk(
        value: JsonElement,
        ref: String,
        currentFile: String,
        path: JsonPath,
    ) {
        val file = ref.substringBefore('#').ifEmpty { currentFile }
        val key = ref.substringAfterLast('/')
        val document = schemas[file] ?: return
        val schema = (document["\$defs"] as? JsonObject)?.get(key)?.jsonObject ?: return

        // A token, and the leaf this walk exists for.
        if ((schema["x-kompot-kind"] as? JsonPrimitive)?.content == TOKEN) {
            val text = (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return
            uses += TokenUse(path, key, text)
            return
        }

        when {
            // A polymorphic base: the discriminator says which member to descend into. Without it the
            // walk would have to try every branch and would report a token for each one that happened
            // to fit.
            schema["discriminator"] != null -> {
                val mapping = (schema["discriminator"] as JsonObject)["mapping"] as? JsonObject ?: return
                val wireType = (value as? JsonObject)?.get(KompotProtocol.DISCRIMINATOR) as? JsonPrimitive ?: return
                val target = mapping[wireType.content] as? JsonPrimitive ?: return
                walk(value, target.content, file, path)
            }

            value is JsonObject -> {
                val properties = schema["properties"] as? JsonObject ?: return
                value.forEach { (name, element) ->
                    val property = properties[name] as? JsonObject ?: return@forEach
                    descend(element, property, file, path + name)
                }
            }

            else -> Unit
        }
    }

    private fun descend(
        value: JsonElement,
        property: JsonObject,
        file: String,
        path: JsonPath,
    ) {
        // A nullable property is printed as anyOf(<the type>, null), so the type has to be dug out of
        // the branches before anything else can be said about it.
        val unwrapped =
            (property["anyOf"] as? JsonArray ?: property["oneOf"] as? JsonArray)
                ?.map { it.jsonObject }
                ?.firstOrNull { branch -> (branch["type"] as? JsonPrimitive)?.content != "null" }
                ?: property

        val reference = (unwrapped["\$ref"] as? JsonPrimitive)?.content
        if (reference != null) {
            walk(value, reference, file, path)
            return
        }

        val items = unwrapped["items"] as? JsonObject ?: return
        (value as? JsonArray)?.forEachIndexed { index, element -> descend(element, items, file, path + index) }
    }

    private companion object {
        const val TOKEN = "token"
    }
}
