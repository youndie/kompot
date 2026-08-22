package io.github.youndie.kompot.spec

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// A build's own wire types could be declared only by writing a Kotlin module, because the profile is
// generated from SerialDescriptors. Everything else had to go through a field of the conformance kit's
// config — invisible to any ordinary JSON Schema library, which therefore saw a closed oneOf and
// rejected a type the deployment had every right to send.
//
// The validator here is constructed WITHOUT extensionTypes on purpose: what is under test is whether
// the artefact alone carries the rule.
class ProfileExtensionsTest {
    private val schemas = KompotSpec.generateAll(KompotToolkitSpec.modules)

    private fun profileWith(extensions: Map<String, Set<String>>): Map<String, JsonObject> =
        schemas.associate { it.fileName to it.document } +
            (KompotProtocol.PROFILE_FILE_NAME to KompotSpec.profile(schemas, extensions))

    private fun errorsFor(
        body: String,
        extensions: Map<String, Set<String>> = emptyMap(),
    ): List<String> =
        JsonSchemaValidator(profileWith(extensions))
            .validate(Json.parseToJsonElement(body), "${KompotProtocol.PROFILE_FILE_NAME}#/\$defs/KompotComponent")

    private val ownType = """{"type":"banking_story","id":"s","title":"Anything at all"}"""

    @Test
    fun `a declared extension is accepted by the profile itself`() {
        assertEquals(emptyList(), errorsFor(ownType, mapOf("KompotComponent" to setOf("banking_story"))))
    }

    // The other half, and the one that makes the first mean something: declaring one type must not
    // open the door to the rest.
    @Test
    fun `an undeclared type is still a violation`() {
        assertTrue(errorsFor(ownType).isNotEmpty())
        assertTrue(errorsFor(ownType, mapOf("KompotComponent" to setOf("something_else"))).isNotEmpty())
    }

    @Test
    fun `declaring an extension does not loosen the types the build really describes`() {
        val extensions = mapOf("KompotComponent" to setOf("banking_story"))

        // A `text` with no `text` property is still wrong: the branch for a known type keeps its shape.
        assertTrue(errorsFor("""{"type":"text","id":"t"}""", extensions).isNotEmpty())
        assertEquals(emptyList(), errorsFor("""{"type":"text","id":"t","text":"Home"}""", extensions))
    }

    // A hierarchy with nothing added must generate exactly what it generated before: a build that
    // declares no extension of its own sees no change at all in its committed profile.
    @Test
    fun `a profile without extensions is byte-identical to the one before this existed`() {
        val plain = KompotSpec.profile(schemas)
        val component = plain.getValue("\$defs").jsonObject.getValue("KompotComponent").jsonObject

        assertEquals(null, component["x-kompot-extensions"])
        assertTrue(plain.getValue("\$defs").jsonObject.keys.none { it.endsWith(KompotProtocol.EXTENSION_SUFFIX) })
    }

    @Test
    fun `the names are readable without parsing the oneOf`() {
        val profile = KompotSpec.profile(schemas, mapOf("KompotAction" to setOf("open_chat", "share")))
        val action = profile.getValue("\$defs").jsonObject.getValue("KompotAction").jsonObject

        assertEquals(
            JsonArray(listOf(JsonPrimitive("open_chat"), JsonPrimitive("share"))),
            action["x-kompot-extensions"],
        )
    }
}
