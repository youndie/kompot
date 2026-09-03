package io.github.youndie.kompot.spec

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// THE RULES A SCHEMA CANNOT EXPRESS, over one body and nothing else.
//
// A schema says what a node may contain. It cannot say that two ids in different subtrees must
// differ, that a string kept in two places must agree with itself, or that a form's schema and its
// screen must name the same fields — those are claims about a WHOLE body, and JSON Schema has no
// vocabulary for them.
//
// They lived inside TckRunner, wrapped in HTTP: walk a server, fetch an endpoint, check the body. The
// checking half never needed the server, and a studio looking at one recorded body needs exactly that
// half. So it moved here as plain functions, and the conformance kit calls the same ones — two copies
// of a rule list is how the copy that does not know about `emptyState` comes to exist.
public data class BodyFinding(
    // The rule's name, in the vocabulary the conformance report already uses: "component-id",
    // "text-spans", "form-fields".
    val rule: String,
    val path: JsonPath,
    val message: String,
) {
    override fun toString(): String = "$path: $message"
}

public object BodyRules {
    // All three, over a body that may be an envelope or a bare tree — the walk finds nodes wherever
    // they are, so nothing here has to know which shape it was handed.
    public fun check(
        body: JsonElement,
        componentTypes: Set<String>,
        crossReferenceKeys: Map<String, String> = emptyMap(),
    ): List<BodyFinding> =
        componentIds(body, componentTypes) + textSpans(body) + formFields(body, crossReferenceKeys)

    // A node's id addresses point updates (SPEC.md §4.2): an empty or duplicated id makes the address
    // ambiguous, and a frame of the update channel lands on the wrong node.
    public fun componentIds(
        body: JsonElement,
        componentTypes: Set<String>,
    ): List<BodyFinding> {
        val components =
            walkJsonObjects(body).filter {
                (it.value[KompotProtocol.DISCRIMINATOR] as? JsonPrimitive)?.content in componentTypes
            }

        val findings = mutableListOf<BodyFinding>()
        val seen = mutableMapOf<String, MutableList<JsonPath>>()

        components.forEach { node ->
            val type = (node.value[KompotProtocol.DISCRIMINATOR] as JsonPrimitive).content
            val id = (node.value["id"] as? JsonPrimitive)?.content
            if (id.isNullOrBlank()) {
                findings += BodyFinding("component-id", node.path, "component \"$type\" has an empty id")
            } else {
                seen.getOrPut(id) { mutableListOf() } += node.path
            }
        }

        // Reported at the SECOND occurrence and every one after it, rather than once for the id: a
        // studio highlights the node a finding points at, and an id with no node behind it points at
        // nothing. The conformance report prints one line per duplicate id, which this still gives it
        // — one per repeat is what "occurs more than once" means from the reading side.
        return findings +
            seen
                .filterValues { it.size > 1 }
                .toSortedMap()
                .flatMap { (id, paths) ->
                    paths.drop(1).map { path ->
                        BodyFinding("component-id", path, "id \"$id\" occurs more than once in the tree")
                    }
                }
    }

    // `text` stays the whole string and the spans are its runs, so the two have to agree (SPEC.md
    // §14). One string kept in two places is the shape that drifts, and it drifts INVISIBLY here: a
    // client that reads the spans shows one sentence and a client that reads the flat form shows
    // another, and neither has any way to notice.
    public fun textSpans(body: JsonElement): List<BodyFinding> =
        walkJsonObjects(body)
            .filter { (it.value[KompotProtocol.DISCRIMINATOR] as? JsonPrimitive)?.content == TEXT_TYPE }
            .mapNotNull { node ->
                val spans = node.value["spans"] as? JsonArray ?: return@mapNotNull null
                if (spans.isEmpty()) return@mapNotNull null

                val whole = (node.value["text"] as? JsonPrimitive)?.content.orEmpty()
                val spelled =
                    spans.joinToString("") { span ->
                        ((span as? JsonObject)?.get("text") as? JsonPrimitive)?.content.orEmpty()
                    }

                if (whole == spelled) {
                    null
                } else {
                    val id = (node.value["id"] as? JsonPrimitive)?.content ?: "?"
                    BodyFinding(
                        "text-spans",
                        node.path,
                        "text \"$id\" reads \"$whole\" flat and \"$spelled\" through its spans — " +
                            "a client sees one or the other",
                    )
                }
            }.toList()

    // A form's schema and its screen must agree on fieldId, and the cross-references of rules and
    // conditions must point at fields that exist (SPEC.md §9.2, §9.3).
    //
    // Silently nothing for a body that is not a form response: this is one of three rules run over
    // every body, and a form check that complained about a screen would make the other two unusable.
    public fun formFields(
        body: JsonElement,
        crossReferenceKeys: Map<String, String> = emptyMap(),
    ): List<BodyFinding> {
        val response = body as? JsonObject ?: return emptyList()
        val schema = response["schema"] as? JsonObject ?: return emptyList()
        val screen = response["screen"] ?: return emptyList()

        val declared =
            (schema["fields"] as? JsonArray)
                .orEmpty()
                .mapNotNull { ((it as? JsonObject)?.get("fieldId") as? JsonPrimitive)?.content }
                .toSet()

        val referencedAt =
            walkJsonObjects(screen)
                .mapNotNull { node ->
                    val field =
                        (node.value["fieldId"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                            ?: return@mapNotNull null
                    field to (JsonPath.ROOT + "screen").append(node.path)
                }.toList()

        val crossReferencedAt =
            walkJsonObjects(schema)
                .mapNotNull { node ->
                    val type =
                        (node.value[KompotProtocol.DISCRIMINATOR] as? JsonPrimitive)?.content
                            ?: return@mapNotNull null
                    val key = crossReferenceKeys[type] ?: return@mapNotNull null
                    val target = (node.value[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
                        ?: return@mapNotNull null
                    target to (JsonPath.ROOT + "schema").append(node.path)
                }.toList()

        val referenced = referencedAt.map { it.first }.toSet()
        val crossReferences = crossReferencedAt.map { it.first }.toSet()

        return referencedAt
            .filter { it.first !in declared }
            .map { (field, path) ->
                BodyFinding("form-fields", path, "a component refers to undeclared field \"$field\"")
            } +
            (declared - referenced).sorted().map { field ->
                // At the schema, because there is no node on the screen to point at — that is the
                // whole complaint.
                BodyFinding("form-fields", JsonPath.ROOT + "schema", "field \"$field\" is declared but never rendered")
            } +
            crossReferencedAt
                .filter { it.first !in declared }
                .map { (field, path) ->
                    BodyFinding("form-fields", path, "a cross-reference points at non-existent field \"$field\"")
                }
    }

    private const val TEXT_TYPE = "text"
}

private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
