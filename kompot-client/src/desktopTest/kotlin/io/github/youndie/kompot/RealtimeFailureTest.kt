package io.github.youndie.kompot

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.kompot.realtime.KompotRealtimeSource
import io.github.youndie.kompot.realtime.UpdateComponentMessage
import io.github.youndie.kompot.standard.TextComponent
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// A failed update channel used to end in an empty catch: the screen kept its last tree, stopped
// changing, and nothing anywhere said so. These hold the report, and hold that the screen still
// survives it — the report must not cost the degradation it describes.
@OptIn(ExperimentalTestApi::class)
class RealtimeFailureTest {
    private data class Failure(
        val topic: String,
        val cause: Throwable,
    )

    private val failures = mutableListOf<Failure>()

    // An object rather than a lambda: routing the realtime report is what overriding the default
    // member is for, and a lambda sink cannot.
    private val sink =
        object : KompotDegradationSink {
            override fun onUnknown(
                kind: KompotDegradationKind,
                originalType: String,
                outcome: KompotDegradationOutcome,
            ) = Unit

            override fun onRealtimeFailure(
                topic: String,
                cause: Throwable,
            ) {
                failures += Failure(topic, cause)
            }
        }

    private val registry = KompotRegistry(mapOf(TextComponent::class to TextRenderer()))

    @Test
    fun `a channel that fails is reported with its topic and cause, and the screen keeps what it had`() =
        runDesktopComposeUiTest {
            val source =
                KompotRealtimeSource {
                    flow {
                        emit(UpdateComponentMessage("greeting", TextComponent(id = "greeting", text = "Updated before the failure")))
                        throw IllegalStateException("channel closed: 401")
                    }
                }

            setContent {
                TestKompotTheme {
                    CompositionLocalProvider(
                        LocalKompotRegistry provides registry,
                        LocalKompotDegradationSink provides sink,
                    ) {
                        KompotRealtimeProvider(topic = "home:user1", source = source, content = {
                            registry.RenderNode(
                                component = TextComponent(id = "greeting", text = "Hello"),
                                actionHandler = recordingActionHandler(),
                                formController = testFormController(),
                            )
                        })
                    }
                }
            }

            waitForIdle()
            assertEquals(1, failures.size, "one failure, reported once")
            assertEquals("home:user1", failures.single().topic)
            assertEquals("channel closed: 401", failures.single().cause.message)
            onNodeWithText("Updated before the failure").assertIsDisplayed()
        }

    @Test
    fun `leaving the screen ends the subscription and is not reported as a failure`() =
        runDesktopComposeUiTest {
            var subscribed = false
            val source =
                KompotRealtimeSource {
                    flow<UpdateComponentMessage> {
                        subscribed = true
                        awaitCancellation()
                    }
                }
            var shown by mutableStateOf(true)

            setContent {
                TestKompotTheme {
                    CompositionLocalProvider(
                        LocalKompotRegistry provides registry,
                        LocalKompotDegradationSink provides sink,
                    ) {
                        if (shown) {
                            KompotRealtimeProvider(topic = "home:user1", source = source, content = {})
                        }
                    }
                }
            }

            waitForIdle()
            assertTrue(subscribed, "the control: the subscription was live before the screen left")
            shown = false
            waitForIdle()
            assertEquals(emptyList(), failures)
        }
}
