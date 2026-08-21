package io.github.youndie.kompot

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.kompot.standard.ButtonComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.TextComponent
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class KompotRegistryFallbackTest {
    @Test
    fun `a component type with no registered renderer falls back to the Unknown component placeholder`() =
        runDesktopComposeUiTest {
                // The registry knows TextComponent but NOT ButtonComponent: both types are valid and
                // both are known to the serialiser, but THIS registry instance cannot draw a button —
                // an application forgot to merge a renderer plug-in, say.
            val partialRegistry = KompotRegistry(mapOf(TextComponent::class to TextRenderer()))

            setContent {
                TestKompotTheme {
                    CompositionLocalProvider(LocalKompotRegistry provides partialRegistry) {
                        partialRegistry.RenderNode(
                            component = ButtonComponent(id = "btn", text = "Pay", action = NavigateAction(deeplink = "/pay")),
                            actionHandler = recordingActionHandler(),
                            formController = testFormController(),
                        )
                    }
                }
            }

            onNodeWithText("Unknown component").assertIsDisplayed()
        }

    @Test
    fun `a component type WITH a registered renderer renders normally instead of the placeholder`() =
        runDesktopComposeUiTest {
            val partialRegistry = KompotRegistry(mapOf(TextComponent::class to TextRenderer()))

            setContent {
                TestKompotTheme {
                    CompositionLocalProvider(LocalKompotRegistry provides partialRegistry) {
                        partialRegistry.RenderNode(
                            component = TextComponent(id = "t", text = "Hello"),
                            actionHandler = recordingActionHandler(),
                            formController = testFormController(),
                        )
                    }
                }
            }

            onNodeWithText("Hello").assertIsDisplayed()
        }

    @Test
    fun `UnknownComponent (an already-unrecognized wire type) also falls back to the placeholder`() =
        runDesktopComposeUiTest {
            val registryWithoutUnknownRenderer = KompotRegistry(emptyMap())

            setContent {
                TestKompotTheme {
                    CompositionLocalProvider(LocalKompotRegistry provides registryWithoutUnknownRenderer) {
                        registryWithoutUnknownRenderer.RenderNode(
                            component = UnknownComponent(id = "u", originalType = "future_widget"),
                            actionHandler = recordingActionHandler(),
                            formController = testFormController(),
                        )
                    }
                }
            }

            onNodeWithText("Unknown component").assertIsDisplayed()
        }
}
