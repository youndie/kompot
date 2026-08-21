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
class RealtimeUpdatesTest {
    @Test
    fun `a component with no matching realtime update renders the original as before`() =
        runDesktopComposeUiTest {
            val registry = KompotRegistry(mapOf(TextComponent::class to TextRenderer()))

            setContent {
                TestKompotTheme {
                    CompositionLocalProvider(LocalKompotRegistry provides registry) {
                        registry.RenderNode(
                            component = TextComponent(id = "greeting", text = "Hello"),
                            actionHandler = recordingActionHandler(),
                            formController = testFormController(),
                        )
                    }
                }
            }

            onNodeWithText("Hello").assertIsDisplayed()
        }

    @Test
    fun `a realtime update matching the component id renders the updated payload instead`() =
        runDesktopComposeUiTest {
            val registry = KompotRegistry(mapOf(TextComponent::class to TextRenderer()))
            val updates = mapOf("greeting" to TextComponent(id = "greeting", text = "Updated from the server"))

            setContent {
                TestKompotTheme {
                    CompositionLocalProvider(
                        LocalKompotRegistry provides registry,
                        LocalKompotRealtimeUpdates provides updates,
                    ) {
                        registry.RenderNode(
                            component = TextComponent(id = "greeting", text = "Hello"),
                            actionHandler = recordingActionHandler(),
                            formController = testFormController(),
                        )
                    }
                }
            }

            onNodeWithText("Updated from the server").assertIsDisplayed()
            onNodeWithText("Hello").assertDoesNotExist()
        }

    @Test
    fun `an update for a different component id does not affect this node`() =
        runDesktopComposeUiTest {
            val registry = KompotRegistry(mapOf(TextComponent::class to TextRenderer()))
            val updates = mapOf("some_other_id" to TextComponent(id = "some_other_id", text = "Not from here"))

            setContent {
                TestKompotTheme {
                    CompositionLocalProvider(
                        LocalKompotRegistry provides registry,
                        LocalKompotRealtimeUpdates provides updates,
                    ) {
                        registry.RenderNode(
                            component = TextComponent(id = "greeting", text = "Hello"),
                            actionHandler = recordingActionHandler(),
                            formController = testFormController(),
                        )
                    }
                }
            }

            onNodeWithText("Hello").assertIsDisplayed()
        }

    @Test
    fun `a realtime update can swap the component type entirely, dispatched by its new type`() =
        runDesktopComposeUiTest {
                // An update changes the TYPE of a node, not only its content — a text became a link,
                // say — so the renderer lookup must go by actual::class rather than the original T.
            val registry =
                KompotRegistry(
                    mapOf(
                        TextComponent::class to TextRenderer(),
                        ButtonComponent::class to ButtonRenderer(),
                    ),
                )
            val updates =
                mapOf(
                    "greeting" to
                        ButtonComponent(
                            id = "greeting",
                            text = "Now it is a button",
                            action = NavigateAction(deeplink = "/x"),
                        ),
                )

            setContent {
                TestKompotTheme {
                    CompositionLocalProvider(
                        LocalKompotRegistry provides registry,
                        LocalKompotRealtimeUpdates provides updates,
                    ) {
                        registry.RenderNode(
                            component = TextComponent(id = "greeting", text = "Hello"),
                            actionHandler = recordingActionHandler(),
                            formController = testFormController(),
                        )
                    }
                }
            }

            onNodeWithText("Now it is a button").assertIsDisplayed()
        }
}
