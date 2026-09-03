package io.github.youndie.kompot.preview

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextStyle
import io.github.youndie.kompot.ColorToken
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotDesignSystem
import io.github.youndie.kompot.KompotRegistry
import io.github.youndie.kompot.TypographyToken
import io.github.youndie.kompot.form.FormSchema
import io.github.youndie.kompot.form.standard.formStandardSerializersModule
import io.github.youndie.kompot.forms.KompotFormResponse
import io.github.youndie.kompot.kompotCoreRenderers
import io.github.youndie.kompot.kompotJson
import io.github.youndie.kompot.kompotStandardRenderers
import io.github.youndie.kompot.realtime.KompotScreenResponse
import io.github.youndie.kompot.standard.KompotPageLoader
import io.github.youndie.kompot.standard.KompotPageResponse
import io.github.youndie.kompot.standard.PaginatedListComponent
import io.github.youndie.kompot.standard.TextComponent
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The seam the studio reads a body through, and it has to be the SAME seam the render decodes by:
// "what shapes does a response come in" is a fact about the protocol, and two copies of it disagree
// the day a fourth envelope appears — without failing, by drawing the envelope instead of the screen.
@OptIn(ExperimentalTestApi::class)
class DecodeKompotBodyTest {
    private val json = kompotJson(formStandardSerializersModule)

    private val screen = TextComponent(id = "only", text = "Catalogue")

    @Test
    fun `a bare component tree decodes to itself, with no schema and no channel`() {
        val body = json.encodeToString(PolymorphicSerializer(KompotComponent::class), screen)

        val decoded = json.decodeKompotBody(body)

        assertEquals(screen, decoded.screen)
        assertEquals(emptyList(), decoded.schema.fields)
        assertNull(decoded.realtimeTopic)
    }

    @Test
    fun `a screen response yields the tree and the channel its updates arrive on`() {
        val body =
            json.encodeToString(
                KompotScreenResponse.serializer(),
                KompotScreenResponse(screen = screen, realtimeTopic = "sweep:42"),
            )

        val decoded = json.decodeKompotBody(body)

        assertEquals(screen, decoded.screen)
        // The topic was dropped on the floor before this function existed — the harness had no field
        // to put it in — and a studio subscribing to live updates needs it.
        assertEquals("sweep:42", decoded.realtimeTopic)
    }

    @Test
    fun `a form response yields the tree and the schema behind it`() {
        val body =
            json.encodeToString(
                KompotFormResponse.serializer(),
                KompotFormResponse(schema = FormSchema(formId = "f", fields = emptyList()), screen = screen),
            )

        val decoded = json.decodeKompotBody(body)

        assertEquals(screen, decoded.screen)
        assertEquals("f", decoded.schema.formId)
        assertNull(decoded.realtimeTopic)
    }

    @Test
    fun `the shape is read from what the body carries`() {
        // The rule itself, separately from the decode: the studio asks this question about a body it
        // never decodes, and a tree that answered it differently would point at the wrong root.
        fun shapeOf(body: String) = kompotBodyShape(Json.parseToJsonElement(body).jsonObject)

        assertEquals(KompotBodyShape.FORM, shapeOf("""{"schema":{"formId":"f","fields":[]},"screen":{}}"""))
        assertEquals(KompotBodyShape.SCREEN, shapeOf("""{"screen":{},"realtimeTopic":"t"}"""))
        assertEquals(KompotBodyShape.COMPONENT, shapeOf("""{"type":"text","id":"a","text":"hi"}"""))

        // A form response carries BOTH properties, so the order of the branches is the rule rather
        // than an accident: read the other way round, every form would decode as a screen and lose
        // its schema.
        assertEquals("screen", KompotBodyShape.FORM.screenProperty)
        assertNull(KompotBodyShape.COMPONENT.screenProperty)
    }

    @Test
    fun `a paginated list draws when a page loader is passed and fails when it is not`() {
        val body =
            json.encodeToString(
                PolymorphicSerializer(KompotComponent::class),
                PaginatedListComponent(
                    id = "feed",
                    initialItems = listOf(TextComponent(id = "row", text = "First page")),
                ),
            )

        // Without one, loudly. A preview that quietly supplied an empty page would photograph a list
        // that ends where it does not, and the golden would pass for as long as the loader was
        // missing — the same class of defect as a grey placeholder recorded as the screen.
        val failure =
            assertFails {
                runDesktopComposeUiTest {
                    setContent { Preview(body, pageLoader = null) }
                }
            }
        assertTrue(
            generateSequence(failure) { it.cause }.mapNotNull { it.message }.any { "PageLoader" in it },
            "the failure did not name the missing page loader: ${failure.message}",
        )

        runDesktopComposeUiTest {
            setContent { Preview(body, pageLoader = EmptyPages) }
            onNodeWithText("First page").assertIsDisplayed()
        }
    }

    @Composable
    private fun Preview(
        body: String,
        pageLoader: KompotPageLoader?,
    ) {
        MaterialTheme {
            KompotPreview(
                body = body,
                registry = KompotRegistry(kompotCoreRenderers + kompotStandardRenderers),
                designSystem = FlatDesignSystem,
                json = json,
                pageLoader = pageLoader,
            )
        }
    }

    private object EmptyPages : KompotPageLoader {
        override suspend fun loadPage(
            url: String,
            params: Map<String, String>,
        ): KompotPageResponse = KompotPageResponse(items = emptyList())
    }

    private object FlatDesignSystem : KompotDesignSystem {
        @Composable
        override fun resolveColor(token: ColorToken): Color = Color.Black

        @Composable
        override fun resolveTypography(token: TypographyToken): TextStyle = MaterialTheme.typography.bodyLarge
    }
}
