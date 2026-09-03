package io.github.youndie.kompot.studio.diagnostics

import io.github.youndie.kompot.KompotDegradationKind
import io.github.youndie.kompot.spec.BodyRules
import io.github.youndie.kompot.spec.JsonSchemaValidator
import io.github.youndie.kompot.spec.KompotProtocol
import io.github.youndie.kompot.spec.childSlots
import io.github.youndie.kompot.spec.paginatingTypes
import io.github.youndie.kompot.spec.walkJsonObjects
import io.github.youndie.kompot.studio.KompotStudioConfig
import kotlinx.serialization.SerializationException
import io.github.youndie.kompot.studio.palette.definitionOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

// FOUR SOURCES, ONE RECORD. "A screen ships without a client release" means the compiler never sees
// the screen — so everything that would have been a compile error has to be somebody's check, and
// each of these four answers a different question about the same body:
//
//   syntax  — is it JSON at all
//   schema  — is it a body of THIS build's profile
//   rules   — is it a body by the rules a schema cannot express (ids, text/spans, form fields)
//   render  — will THIS client draw it
//   vocabulary — is every open word and every token one this build actually answers for
//
// None of them is invented here. Layers 2 and 3 are the conformance kit's, layer 4 is the real
// render reporting on itself, and the studio's whole job is to put them in one list with one shape.
internal enum class Severity {
    // It is wrong: the body would not decode, or would decode into something the profile forbids.
    ERROR,

    // It draws, and not as intended. A degradation is the protocol working — an unfamiliar node
    // becomes a placeholder rather than taking the screen down — so calling it an error would be
    // calling the design a defect. It is still the thing somebody opened the studio to see.
    WARNING,
}

internal data class Finding(
    val layer: String,
    // The validator's notation, and the notation ScreenNode carries, so clicking a finding can select
    // a row without either side parsing the other. Null where there is no node to point at — a body
    // that does not parse has no tree.
    val path: String?,
    val message: String,
    val severity: Severity,
    // Where in the TEXT, for the one layer that has an offset and no node: a body that does not parse
    // has no tree to point at, and the character the parser gave up on is the only place there is.
    val offset: Int? = null,
)

// Layers 1 to 3, over the text. Layer 4 arrives from the render and is folded in by the window.
internal fun diagnose(
    config: KompotStudioConfig,
    body: String,
): List<Finding> {
    val element =
        try {
            Json.parseToJsonElement(body)
        } catch (e: SerializationException) {
            // First and ALONE: a body that does not parse has no tree to check against a schema, and a
            // validator handed a half-typed object reports the absence of everything that was going to
            // be typed next — a page of findings, none of them the one that matters.
            return listOf(syntaxFinding(body, e))
        }

    val schemaFindings =
        validatorFor(config).validate(element, COMPONENT_REF).map { finding ->
            Finding("schema", finding.path.toString(), finding.message, Severity.ERROR)
        }

    val ruleFindings =
        BodyRules
            .check(element, componentTypesFor(config), config.crossReferenceKeys)
            .map { finding ->
                Finding("rules:${finding.rule}", finding.path.toString(), finding.message, Severity.ERROR)
            }

    return schemaFindings + ruleFindings + vocabularyFindings(config, element) +
        stubbedPagination(config, element) + unfilledFields(config, element)
}

// A LIST DRAWN WITH A STUBBED LOADER IS NOT A GOLDEN, and nothing else on the frame says so.
//
// KompotPreview refuses to draw a paginated list without a page loader on purpose: a quietly supplied
// empty page photographs a list ending where it does not, and the golden then passes for as long as
// the loader is missing. A studio, though, has to open the screen — half a real application's screens
// are lists — so a consumer passes a stub, and takes on the very hazard the default guards against.
// The Capture button turns that hazard into a file.
//
// So the frame says it. Structural rather than counted: a list short enough to fit asks for nothing,
// so "the loader was called" is silent in exactly the case a person is most likely to photograph.
internal fun stubbedPagination(
    config: KompotStudioConfig,
    body: JsonElement,
): List<Finding> {
    if (config.pageLoader == null) return emptyList()
    val paginating = paginatingTypesFor(config)

    return walkJsonObjects(body)
        .filter { (it.value[DISCRIMINATOR] as? JsonPrimitive)?.content in paginating }
        .map { node ->
            Finding(
                layer = "render",
                path = node.path.toString(),
                message =
                    "this list is drawn with a stubbed page loader — the next page was never asked " +
                        "for, so a captured frame is not a golden",
                severity = Severity.WARNING,
            )
        }.toList()
}

// A NODE THAT WAS ADDED AND NOT YET FILLED IN.
//
// The palette writes a new component's required properties and nothing more, so a fresh `text` has an
// empty string where its words go. Nothing below this line calls that wrong — an empty string IS a
// string, so the schema layer is satisfied, and a server is free to send one — which is exactly why
// it needs saying here: the studio is where somebody is building the screen, and a required field
// left blank is the unfinished half of the drop they just made rather than a body defect.
//
// A warning, not an error, and only for properties the schema calls required: an optional caption
// somebody deliberately blanked is their business.
internal fun unfilledFields(
    config: KompotStudioConfig,
    body: JsonElement,
): List<Finding> =
    walkJsonObjects(body)
        .flatMap { node ->
            val wireType = (node.value[DISCRIMINATOR] as? JsonPrimitive)?.content ?: return@flatMap emptySequence()
            val definition = definitionOf(config, wireType) ?: return@flatMap emptySequence()
            val required = (definition["required"] as? JsonArray).orEmpty().map { (it as JsonPrimitive).content }

            required
                .asSequence()
                .filter { name -> (node.value[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.isBlank() == true }
                .map { name ->
                    Finding(
                        layer = "draft",
                        path = node.path.toString(),
                        message = "\"$name\" is required and empty — this node was added but not filled in",
                        severity = Severity.WARNING,
                    )
                }
        }.toList()

private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()

internal fun capturingIsSafe(
    config: KompotStudioConfig,
    body: String,
): Boolean =
    runCatching { stubbedPagination(config, Json.parseToJsonElement(body)).isEmpty() }.getOrDefault(true)

private const val DISCRIMINATOR = "type"

private val paginating = mutableMapOf<KompotStudioConfig, Set<String>>()

private fun paginatingTypesFor(config: KompotStudioConfig): Set<String> =
    paginating.getOrPut(config) { paginatingTypes(config.schemas) }

// Layer 4: what the real render reported while drawing this body.
internal fun degradationFinding(
    kind: KompotDegradationKind,
    originalType: String,
): Finding {
    val hint =
        if (originalType == NO_DISCRIMINATOR) {
            " — the body carried no \"type\" on that node, which is what a CONCRETE serialiser writes " +
                "for the root it is handed. Encode the root polymorphically (respondKompotComponent) " +
                "and the discriminator comes back."
        } else {
            ""
        }

    return Finding(
        layer = "render",
        // A degradation names a TYPE and not a place: the sink is told what could not be drawn, not
        // where it sat. Joining it to a node would mean guessing, and a row that highlights the wrong
        // node is worse than one that highlights none.
        path = null,
        message = "$kind: \"$originalType\"$hint",
        severity = Severity.WARNING,
    )
}

// The one value KompotPreview's own failure message calls out by name: the type an open hierarchy
// decodes to when the body named none at all. Worth telling apart from an unregistered type, because
// the cause is specific — and because it is the mistake the whole "preview a body, not an object"
// design exists to catch.
private const val NO_DISCRIMINATOR = "unknown"

internal const val COMPONENT_REF: String = "${KompotProtocol.PROFILE_FILE_NAME}#/\$defs/KompotComponent"

private val OFFSET = Regex("offset (\\d+)")

// kotlinx.serialization reports a character offset inside the message and offers it nowhere else, so
// it is read back out of the sentence. A hack, and a contained one: the worst it can do is fail to
// find a number, and then the message stands on its own exactly as before.
private fun syntaxFinding(
    body: String,
    failure: SerializationException,
): Finding {
    val message = failure.message.orEmpty()
    val offset = OFFSET.find(message)?.groupValues?.get(1)?.toIntOrNull()

    if (offset == null || offset > body.length) {
        return Finding("syntax", null, message, Severity.ERROR)
    }

    val before = body.take(offset)
    val line = before.count { it == '\n' } + 1
    val column = offset - before.lastIndexOf('\n')

    return Finding("syntax", null, "line $line, column $column: $message", Severity.ERROR, offset = offset)
}

// Built per configuration and cached against it: a validator is expensive to assemble and the
// configuration does not change while a window is open.
private val validators = mutableMapOf<KompotStudioConfig, JsonSchemaValidator>()

private val componentTypes = mutableMapOf<KompotStudioConfig, Set<String>>()

private fun validatorFor(config: KompotStudioConfig): JsonSchemaValidator =
    validators.getOrPut(config) {
        JsonSchemaValidator(
            documents = config.schemas,
            // Strict: without the profile standing in for the open base, every nested child is checked
            // against "any component" and a body full of invented types passes.
            strictProfile = config.schemas[KompotProtocol.PROFILE_FILE_NAME],
            extensionTypes = config.extensionTypes,
        )
    }

// The closed list this build can receive, read from the same place the tree reads its slots — so a
// node the tree marks unfamiliar is the same node the id rule skips.
private fun componentTypesFor(config: KompotStudioConfig): Set<String> =
    componentTypes.getOrPut(config) { childSlots(config.schemas).keys }
