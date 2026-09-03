package io.github.youndie.kompot.spec

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// A finding says WHERE, in a form something can act on.
//
// The path was always computed and then thrown away into the front of a sentence: enough for a report
// a person reads, useless to a studio that has to highlight the tree row it belongs to. Getting it
// back meant parsing the validator's own prefix out of the validator's own message.
class SchemaFindingTest {
    private val documents: Map<String, JsonObject> =
        KompotSpec.generateAll(KompotToolkitSpec.modules).let { schemas ->
            schemas.associate { it.fileName to it.document } +
                (KompotProtocol.PROFILE_FILE_NAME to KompotSpec.profile(schemas))
        }

    private val validator =
        JsonSchemaValidator(documents, strictProfile = documents[KompotProtocol.PROFILE_FILE_NAME])

    @Test
    fun `a finding carries the segments of the node it is about and the keyword that refused it`() {
        // A text node inside a screen envelope, missing the id every component must carry.
        val body = """{"screen":{"type":"column","id":"root","children":[{"type":"text","text":"hi"}]}}"""

        val findings =
            validator.validate(
                Json.parseToJsonElement(body),
                "kompot-realtime.schema.json#/\$defs/KompotScreenResponse",
            )

        val missingId = findings.single { it.keyword == "required" }

        assertEquals(
            listOf(
                JsonPath.Segment.Name("screen"),
                JsonPath.Segment.Name("children"),
                JsonPath.Segment.Index(0),
            ),
            missingId.path.segments,
        )
        assertTrue("id" in missingId.message, "the message stopped naming the property: ${missingId.message}")
    }

    @Test
    fun `printing a finding gives the line every report has always carried`() {
        val body = """{"screen":{"type":"column","id":"root","children":[{"type":"text","text":"hi"}]}}"""

        val findings =
            validator.validate(
                Json.parseToJsonElement(body),
                "kompot-realtime.schema.json#/\$defs/KompotScreenResponse",
            )

        // The whole point of keeping toString(): the TCK's report, and every expectation written
        // against it, must not move because the type behind it changed.
        assertEquals(
            """${'$'}.screen.children[0]: required property "id" is missing""",
            findings.single { it.keyword == "required" }.toString(),
        )
    }

    @Test
    fun `the path a finding carries is the notation a tree node carries`() {
        val body = """{"type":"column","id":"root","children":[{"type":"text","id":"t"},{"type":"nonesuch"}]}"""

        val findings =
            validator.validate(
                Json.parseToJsonElement(body),
                "${KompotProtocol.PROFILE_FILE_NAME}#/\$defs/KompotComponent",
            )

        // walkJsonObjects prints the same string for the same node, which is what lets a studio join
        // findings to rows without either side parsing the other.
        val paths = walkJsonObjects(Json.parseToJsonElement(body)).map { it.path.toString() }.toSet()
        assertTrue(findings.isNotEmpty(), "an invented type produced no finding at all")
        findings.forEach { finding ->
            assertTrue(
                finding.path.toString() in paths,
                "no node stands at ${finding.path} — the two notations have drifted",
            )
        }
    }

    @Test
    fun `a clean body produces no findings`() {
        // The control. Without it every assertion above is satisfied by a validator that complains
        // about everything.
        val body = """{"type":"column","id":"root","children":[{"type":"text","id":"t","text":"hi"}]}"""

        assertEquals(
            emptyList(),
            validator.validate(
                Json.parseToJsonElement(body),
                "${KompotProtocol.PROFILE_FILE_NAME}#/\$defs/KompotComponent",
            ),
        )
    }
}
