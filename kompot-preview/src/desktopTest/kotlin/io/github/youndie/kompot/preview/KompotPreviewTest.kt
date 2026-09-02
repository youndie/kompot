package io.github.youndie.kompot.preview

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextStyle
import io.github.youndie.kompot.ColorToken
import io.github.youndie.kompot.KompotDesignSystem
import io.github.youndie.kompot.KompotRegistry
import io.github.youndie.kompot.TypographyToken
import io.github.youndie.kompot.form.standard.TextValue
import io.github.youndie.kompot.form.standard.formStandardSerializersModule
import io.github.youndie.kompot.form.standard.required
import io.github.youndie.kompot.forms.KompotFormResponse
import io.github.youndie.kompot.forms.standard.boundTextInput
import io.github.youndie.kompot.forms.standard.buildFormScreen
import io.github.youndie.kompot.generated.generatedFormsClientRenderers
import io.github.youndie.kompot.kompotCoreRenderers
import io.github.youndie.kompot.kompotJson
import io.github.youndie.kompot.kompotStandardRenderers
import io.github.youndie.kompot.standard.ButtonComponent
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.TextComponent
import io.github.youndie.kompot.standard.text
import io.github.youndie.kompot.standard.CloseAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class KompotPreviewTest {
    private val registry = KompotRegistry(kompotCoreRenderers + kompotStandardRenderers + generatedFormsClientRenderers)

    private val designSystem =
        object : KompotDesignSystem {
            @Composable
            override fun resolveColor(token: ColorToken): Color = Color.Black

            @Composable
            override fun resolveTypography(token: TypographyToken): TextStyle = TextStyle.Default
        }

    // The same Json on both sides of every test, and not for tidiness: the engine's own module does
    // not carry form-standard's field definitions, so an application adds them — and a preview must
    // speak exactly what its client speaks or it is a picture of a screen the client cannot decode.
    private val json = kompotJson(formStandardSerializersModule)

    @Composable
    private fun Preview(
        body: String,
        state: KompotPreviewState = KompotPreviewState(),
    ) {
        MaterialTheme {
            KompotPreview(body = body, registry = registry, designSystem = designSystem, state = state, json = json)
        }
    }

    private fun checkoutBody(): String {
        val response =
            buildFormScreen("checkout") {
                text(id = "title", text = "Checkout")
                boundTextInput(fieldId = "name", label = "Name") { required("Enter a name") }
            }
        return json.encodeToString(KompotFormResponse.serializer(), response)
    }

    // A form body carries its schema, so the preview builds the controller the screen needs without
    // being told which of the three shapes it was handed.
    @Test
    fun `a form body is drawn with its fields`() =
        withMainDispatcher {
            runDesktopComposeUiTest {
                setContent { Preview(checkoutBody()) }

                onNodeWithText("Checkout").assertIsDisplayed()
                onNodeWithText("Name").assertIsDisplayed()
            }
        }

    // The state is the half a picture of a form leaves out: the same body, three images. Errors show
    // on a field somebody has left, so "what the form says about an empty submit" needs the flag
    // rather than a faked tap on the button.
    @Test
    fun `an untouched form shows no error and a touched one shows every error`() =
        withMainDispatcher {
            val body = checkoutBody()

            runDesktopComposeUiTest {
                setContent { Preview(body) }
                onAllNodesWithText("Enter a name").assertCountEquals(0)
            }

            runDesktopComposeUiTest {
                setContent { Preview(body, KompotPreviewState(allFieldsChanged = true)) }
                onNodeWithText("Enter a name").assertIsDisplayed()
            }
        }

    @Test
    fun `prefilled values are drawn`() =
        withMainDispatcher {
            runDesktopComposeUiTest {
                setContent { Preview(checkoutBody(), KompotPreviewState(values = mapOf("name" to TextValue("Ada")))) }

                onNodeWithText("Ada").assertIsDisplayed()
            }
        }

    // The reason the input is a body rather than a component, in one test — and the reason the
    // degradation sink is loud, in the same one.
    //
    // A tree serialised the way a plain call.respond does it loses the "type" discriminator on its
    // ROOT and keeps it on every child. Nothing throws: the hierarchy is open, so the root decodes to
    // UnknownComponent and the screen degrades to a placeholder, exactly as it would in front of a
    // person. From the object in memory the same screen renders perfectly.
    //
    // So a preview from the object photographs a working screen that does not work, and a preview
    // from the body with a quiet sink photographs the grey placeholder and records it as the expected
    // appearance. It takes both decisions to make this test fail for the right reason.
    @Test
    fun `a body whose root lost its discriminator is reported rather than photographed`() =
        withMainDispatcher {
            val tree =
                ColumnComponent(
                    id = "root",
                    children =
                        listOf(
                            TextComponent(id = "t", text = "Catalogue"),
                            ButtonComponent(id = "b", text = "Buy", action = CloseAction),
                        ),
                )
            // Exactly what call.respond(component) produces: the serialiser resolved from the concrete
            // runtime class, which writes no discriminator for the root it was given.
            val body = json.encodeToString(ColumnComponent.serializer(), tree)
            assertTrue("\"type\"" in body, "the children still carry theirs — that is what makes the bug survivable")

            var failure: Throwable? = null
            runDesktopComposeUiTest {
                failure = assertFailsWith<Exception> {
                    setContent { Preview(body) }
                    waitForIdle()
                }
            }
            // Asserted on the message, not merely on "something threw": a negative test that accepts
            // any exception also passes when the harness breaks for a reason of its own.
            assertTrue(
                "UNKNOWN_COMPONENT" in (failure?.message ?: ""),
                "expected the preview to report a root it could not recognise, got: ${failure?.message}",
            )
        }

    // Loud by default: a missing renderer draws a grey placeholder on a real screen, which is right
    // there and wrong here — recorded into a golden it becomes the expected appearance of the screen.
    @Test
    fun `a component with no renderer stops the preview instead of being photographed`() =
        withMainDispatcher {
            val emptyRegistry = KompotRegistry(kompotCoreRenderers)

            var failure: Throwable? = null
            runDesktopComposeUiTest {
                failure = assertFailsWith<Exception> {
                    setContent {
                        MaterialTheme {
                            KompotPreview(
                                body = checkoutBody(),
                                registry = emptyRegistry,
                                designSystem = designSystem,
                                json = json,
                            )
                        }
                    }
                    waitForIdle()
                }
            }
            assertTrue(
                "UNRENDERABLE_COMPONENT" in (failure?.message ?: "") || "UNRENDERABLE_COMPONENT" in (failure?.cause?.message ?: ""),
                "expected the preview to name what it could not draw, got: ${failure?.message}",
            )
        }

    private fun withMainDispatcher(block: () -> Unit) {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            block()
        } finally {
            Dispatchers.resetMain()
        }
    }
}
