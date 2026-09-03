package io.github.youndie.kompot.studio.diagnostics

import io.github.youndie.kompot.KompotDegradationKind
import io.github.youndie.kompot.spec.BodyRules
import io.github.youndie.kompot.spec.JsonSchemaValidator
import io.github.youndie.kompot.spec.KompotProtocol
import io.github.youndie.kompot.spec.childSlots
import io.github.youndie.kompot.studio.KompotStudioConfig
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

// FOUR SOURCES, ONE RECORD. "A screen ships without a client release" means the compiler never sees
// the screen — so everything that would have been a compile error has to be somebody's check, and
// each of these four answers a different question about the same body:
//
//   syntax  — is it JSON at all
//   schema  — is it a body of THIS build's profile
//   rules   — is it a body by the rules a schema cannot express (ids, text/spans, form fields)
//   render  — will THIS client draw it
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

    return schemaFindings + ruleFindings
}

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

    return Finding("syntax", null, "line $line, column $column: $message", Severity.ERROR)
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
