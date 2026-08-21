package io.github.youndie.kompot.tck

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

// An endpoint as the kit sees it. Read out of an OpenAPI document — anybody's: an implementation on
// another stack describes itself with the same document carrying x-kompot-endpoint-kind, and the same
// set of checks applies to it without a line of code changing. The addresses themselves may be
// anything at all (SPEC.md §16).
data class TckEndpoint(
    val method: String,
    val path: String,
    val kind: String,
    val secured: Boolean,
    val successStatus: Int,
    val successSchema: String?,
    val successContentType: String?,
    val statuses: Set<Int>,
    val deprecated: Boolean,
) {
    // How the walk records that it reached this endpoint: method and path together, since one path can
    // carry a GET and a POST with entirely different kinds.
    val key: String get() = "$method $path"

    // Checks that walk blind apply only to addresses without placeholders: what to put in {formId} or
    // {id} is not something the kit can know.
    val hasPathParams: Boolean get() = '{' in path

    // For a streaming endpoint (text/event-stream) the body is a sequence of frames rather than one
    // JSON document, and must not be parsed as one.
    val respondsWithJson: Boolean get() = successContentType == "application/json"
}

object TckEndpoints {
    fun fromOpenApi(document: JsonObject): List<TckEndpoint> {
        val paths = document["paths"] as? JsonObject ?: error("The OpenAPI document has no paths")
        val documentSecurity = document["security"] as? JsonArray

        return paths.flatMap { (path, operations) ->
            operations.jsonObject.map { (method, operation) ->
                val json = operation.jsonObject
                val responses = json.getValue("responses").jsonObject
                val statuses = responses.keys.mapNotNull { it.toIntOrNull() }.toSet()
                val success = statuses.filter { it in 200..299 }.minOrNull() ?: error("$method $path: no success status")

                TckEndpoint(
                    method = method.uppercase(),
                    path = path,
                    kind = (json["x-kompot-endpoint-kind"] as? JsonPrimitive)?.content ?: "unknown",
                    secured = securedBy(json["security"] as? JsonArray ?: documentSecurity),
                    successStatus = success,
                    successSchema = successSchema(responses.getValue(success.toString()).jsonObject),
                    successContentType = successContentType(responses.getValue(success.toString()).jsonObject),
                    statuses = statuses,
                    deprecated = (json["deprecated"] as? JsonPrimitive)?.content == "true",
                )
            }
        }
    }

    // Whether an endpoint needs a token. The presence of the `security` key is NOT the question, and
    // reading it that way had the answer backwards in both directions: an operation WITHOUT the key
    // inherits the document's requirement, and `security: []` — the standard way to declare one
    // operation public under a secured document — was read as "secured" precisely because the key was
    // there. A login form is necessarily public, so the kit demanded a 401 from the one screen that
    // must answer 200, and no server could be conformant with a public screen at all.
    private fun securedBy(security: JsonArray?): Boolean = security != null && security.isNotEmpty()

    private fun successContentType(response: JsonObject): String? = (response["content"] as? JsonObject)?.keys?.firstOrNull()

    // The $ref is either directly in the schema or under items of an array — a data source answers a
    // list.
    private fun successSchema(response: JsonObject): String? {
        val content = response["content"] as? JsonObject ?: return null
        val schema = content.values.firstOrNull()?.jsonObject?.get("schema")?.jsonObject ?: return null
        (schema["\$ref"] as? JsonPrimitive)?.let { return it.content }
        return ((schema["items"] as? JsonObject)?.get("\$ref") as? JsonPrimitive)?.content
    }
}
