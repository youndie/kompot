package io.github.youndie.kompot.studio

import io.github.youndie.kompot.spec.JsonSchemaValidator
import io.github.youndie.kompot.spec.KompotProtocol
import io.github.youndie.kompot.spec.KompotSpecResources
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

// The two layers of diagnostics a spike can carry without a consumer: syntax, and the toolkit's own
// closed vocabulary. Layers 3 (body rules from the TCK), 4 (degradations of the real render) and 5
// (a project's vocabulary) are B-12 and B-19; the degradations are collected here already, because
// they arrive from the render rather than from a check and the window has to survive them.

// The toolkit's schemas, read from the jar rather than from a checkout: that is how a CONSUMER will
// read them too, and a spike that read them off disk would be proving something about this
// repository's layout.
private val toolkitSchemas by lazy { KompotSpecResources("kompot-spec").schemas() }

private val validator by lazy {
    JsonSchemaValidator(
        documents = toolkitSchemas,
        // Strict: without the profile standing in for the open base, every nested child is checked
        // against "any component" and a body full of invented types passes.
        strictProfile = toolkitSchemas.getValue(KompotProtocol.PROFILE_FILE_NAME),
    )
}

internal const val COMPONENT_REF: String = "${KompotProtocol.PROFILE_FILE_NAME}#/\$defs/KompotComponent"

internal data class SpikeFinding(val layer: String, val message: String)

// Syntax first and alone: a body that does not parse has no tree to check against a schema, and a
// validator handed a half-typed object reports the absence of everything that was going to be typed
// next.
internal fun diagnose(body: String): List<SpikeFinding> {
    val element =
        try {
            Json.parseToJsonElement(body)
        } catch (e: SerializationException) {
            return listOf(SpikeFinding("syntax", e.message ?: "the body is not JSON"))
        }

    return validator.validate(element, COMPONENT_REF).map { SpikeFinding("schema", it) }
}
