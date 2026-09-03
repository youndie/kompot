package io.github.youndie.kompot.studio

import io.github.youndie.kompot.spec.JsonSchemaValidator
import io.github.youndie.kompot.spec.KompotProtocol
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

// Two of the five layers of diagnostics, and the two that need nothing from a consumer but the
// schemas it already generates: syntax, and the closed list of types. Layer 3 (the body rules carried
// by the TCK), layer 4 (degradations of the real render) and layer 5 (a project's own vocabulary) are
// B-12 and B-19. Layer 4's findings already arrive — from the render rather than from a check — which
// is why the window collects them separately.
// The path travels with the finding now (B-04): the tree row a schema complaint belongs to is
// `finding.path.toString()`, which is the notation ScreenNode already carries. Nothing parses a
// prefix out of a sentence.
internal data class StudioFinding(
    val layer: String,
    val message: String,
    val path: String? = null,
    val keyword: String? = null,
)

// Syntax first and alone: a body that does not parse has no tree to check against a schema, and a
// validator handed a half-typed object reports the absence of everything that was going to be typed
// next.
internal fun diagnose(
    config: KompotStudioConfig,
    body: String,
): List<StudioFinding> {
    val element =
        try {
            Json.parseToJsonElement(body)
        } catch (e: SerializationException) {
            return listOf(StudioFinding("syntax", e.message ?: "the body is not JSON"))
        }

    return validatorFor(config).validate(element, COMPONENT_REF).map { finding ->
        StudioFinding(
            layer = "schema",
            message = finding.message,
            path = finding.path.toString(),
            keyword = finding.keyword,
        )
    }
}

internal const val COMPONENT_REF: String = "${KompotProtocol.PROFILE_FILE_NAME}#/\$defs/KompotComponent"

// Built per configuration and cached against it, because a validator is expensive to assemble and the
// configuration does not change while a window is open.
private val validators = mutableMapOf<KompotStudioConfig, JsonSchemaValidator>()

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
