package io.github.youndie.kompot.spec

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The rule "a deeplink is a URI of the application's own scheme, and NOT a web address" used to be one
// regex with a negative lookahead. It is now a pattern plus a `not`, because RE2 engines — Go's
// standard regexp among them — refuse a lookahead outright and take the whole schema file down with
// it. Rewriting a rule into a different shape is the moment to prove it still rejects what it rejected,
// so these cases run against the GENERATED schema rather than against either constant.
class DeeplinkFormatTest {
    private val documents = KompotSpec.generateAll(KompotToolkitSpec.modules).associate { it.fileName to it.document }
    private val validator = JsonSchemaValidator(documents)

    private fun errorsFor(
        deeplink: String,
        ref: String,
    ): List<String> = validator.validate(json(deeplink), ref).map { it.toString() }

    private fun json(deeplink: String): JsonElement =
        Json.parseToJsonElement("""{"type":"navigate","deeplink":${Json.encodeToString(String.serializer(), deeplink)}}""")

    @Test
    fun `an application deeplink is accepted`() {
        listOf("app://home", "myapp://checkout?tariff=premium", "bank-app://cards/1").forEach { accepted ->
            assertEquals(emptyList(), errorsFor(accepted, NAVIGATE), "$accepted should be a valid deeplink")
        }
    }

    // The half that used to be the lookahead. If `not` were dropped or misspelled, the positive pattern
    // alone would happily accept these — https IS a URI with a scheme.
    @Test
    fun `a web address is rejected, which is the whole point of the rule`() {
        listOf("http://example.com", "https://example.com/path").forEach { rejected ->
            assertTrue(errorsFor(rejected, NAVIGATE).isNotEmpty(), "$rejected should not pass as a deeplink")
        }
    }

    @Test
    fun `something that is not a URI at all is still rejected by the positive half`() {
        listOf("home", "/screens/home", "App://Home").forEach { rejected ->
            assertTrue(errorsFor(rejected, NAVIGATE).isNotEmpty(), "$rejected should not pass as a deeplink")
        }
    }

    // Two files carry the same rule, and a fix applied to one of them is the commonest way to leave the
    // other behind.
    @Test
    fun `the route graph enforces exactly the same rule`() {
        val route = Json.parseToJsonElement("""{"deeplink":"https://example.com","endpoint":"/screens/home"}""")

        assertTrue(validator.validate(route, ROUTE).isNotEmpty())
        assertEquals(
            emptyList(),
            validator.validate(Json.parseToJsonElement("""{"deeplink":"app://home","endpoint":"/screens/home"}"""), ROUTE),
        )
    }

    // No pattern anywhere in the toolkit's schemas may use lookaround, lookbehind or a backreference:
    // an engine that cannot compile one of them validates NOTHING, not merely the field it guards.
    @Test
    fun `every generated pattern stays inside the RE2 subset`() {
        val unsupported = Regex("""\(\?[=!<]|\\\d""")
        val offenders =
            documents.flatMap { (fileName, document) ->
                patternsOf(document).filter { unsupported.containsMatchIn(it) }.map { "$fileName: $it" }
            }

        assertEquals(emptyList(), offenders)
    }

    private fun patternsOf(element: JsonElement): List<String> =
        when (element) {
            is kotlinx.serialization.json.JsonObject ->
                element.flatMap { (key, value) ->
                    if (key == "pattern" && value is kotlinx.serialization.json.JsonPrimitive && value.isString) {
                        listOf(value.content)
                    } else {
                        patternsOf(value)
                    }
                }

            is kotlinx.serialization.json.JsonArray -> element.flatMap { patternsOf(it) }
            else -> emptyList()
        }

    private companion object {
        const val NAVIGATE = "kompot-standard.schema.json#/\$defs/KompotActionNavigate"
        const val ROUTE = "kompot-navigation.schema.json#/\$defs/ScreenRoute"
    }
}
