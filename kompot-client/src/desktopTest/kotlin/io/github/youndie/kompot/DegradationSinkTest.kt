package io.github.youndie.kompot

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.kompot.standard.ButtonComponent
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.TextComponent
import kotlin.test.Test
import kotlin.test.assertEquals

// Degradation converts a crash into a hole. A crash is reported by every crash reporter ever written
// and a hole is reported by nobody — so these cases are about the hole being reportable at all, from
// a place a deployment can route to its own logging rather than to a console nobody is holding.
@OptIn(ExperimentalTestApi::class)
class DegradationSinkTest {
    private data class Reported(
        val kind: KompotDegradationKind,
        val originalType: String,
        val outcome: KompotDegradationOutcome,
    )

    private val reported = mutableListOf<Reported>()
    private val sink = KompotDegradationSink { kind, type, outcome -> reported += Reported(kind, type, outcome) }

    private fun render(
        component: KompotComponent,
        registry: KompotRegistry = KompotRegistry(kompotCoreRenderers + kompotStandardRenderers),
        onAction: (KompotAction) -> Unit = {},
    ) = @androidx.compose.runtime.Composable {
        MaterialTheme {
            CompositionLocalProvider(
                LocalKompotDesignSystem provides TestDesignSystem(),
                LocalKompotRegistry provides registry,
                LocalKompotDegradationSink provides sink,
            ) {
                registry.RenderNode(component, recordingActionHandler(onAction), testFormController())
            }
        }
    }

    @Test
    fun `a type the serializers module did not know is reported, and says nothing was drawn`() =
        runDesktopComposeUiTest {
            setContent(render(UnknownComponent(id = "x", originalType = "promo_banner")))

            assertEquals(
                listOf(Reported(KompotDegradationKind.UNKNOWN_COMPONENT, "promo_banner", KompotDegradationOutcome.NOTHING)),
                reported,
            )
        }

    @Test
    fun `the same type with a server fallback names the server's equivalent`() =
        runDesktopComposeUiTest {
            setContent(
                render(
                    UnknownComponent(
                        id = "x",
                        originalType = "promo_banner",
                        fallback = TextComponent(id = "t", text = "Promo"),
                    ),
                ),
            )

            onNodeWithText("Promo").assertExists()
            assertEquals(
                listOf(
                    Reported(
                        KompotDegradationKind.UNKNOWN_COMPONENT,
                        "promo_banner",
                        KompotDegradationOutcome.SERVER_FALLBACK,
                    ),
                ),
                reported,
            )
        }

    // The second hole: the type decoded fine and this build has no renderer for it. It is reported
    // under the name the SERVER wrote — "text", not TextComponent — because a reader greps logs with
    // the vocabulary of the wire, and for a while this was the one kind that answered in Kotlin.
    @Test
    fun `a component with no renderer is reported by its wire name, with the placeholder as the outcome`() =
        runDesktopComposeUiTest {
            setContent(render(TextComponent(id = "t", text = "hi"), registry = KompotRegistry(kompotCoreRenderers)))

            assertEquals(
                listOf(Reported(KompotDegradationKind.UNRENDERABLE_COMPONENT, "text", KompotDegradationOutcome.PLACEHOLDER)),
                reported,
            )
        }

    // An action nobody can act on, reaching the handler as UnknownAction. Reported where it is raised
    // rather than left to the application to notice a type it does not know.
    @Test
    fun `an action the serializers module did not know is reported when it is raised`() =
        runDesktopComposeUiTest {
            val handled = mutableListOf<KompotAction>()
            setContent(
                render(
                    ButtonComponent(id = "b", text = "Go", action = UnknownAction(originalType = "open_esim")),
                    onAction = { handled += it },
                ),
            )

            onNodeWithText("Go").performClick()
            waitForIdle()

            assertEquals(
                listOf(Reported(KompotDegradationKind.UNKNOWN_ACTION, "open_esim", KompotDegradationOutcome.NOTHING)),
                reported,
            )
            assertEquals(1, handled.size, "the handler must still receive it: reporting is not swallowing")
        }

    // The wrapper is added at every level of the tree, so without a marker on it a tap five nodes deep
    // would be reported five times — and a count is what a rollout is decided on.
    @Test
    fun `an action raised deep in a tree is reported once`() =
        runDesktopComposeUiTest {
            val tree =
                ColumnComponent(
                    id = "a",
                    children = listOf(
                        ColumnComponent(
                            id = "b",
                            children = listOf(
                                ColumnComponent(
                                    id = "c",
                                    children = listOf(
                                        ButtonComponent(id = "d", text = "Go", action = UnknownAction(originalType = "open_esim")),
                                    ),
                                ),
                            ),
                        ),
                    ),
                )
            setContent(render(tree))

            onNodeWithText("Go").performClick()
            waitForIdle()

            assertEquals(1, reported.size, reported.toString())
        }
}
