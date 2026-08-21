package io.github.youndie.kompot

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.kompot.analytics.AnalyticsEvent
import io.github.youndie.kompot.analytics.AnalyticsTracker
import io.github.youndie.kompot.analytics.KompotEventNamingRegistry
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.TextComponent
import kotlin.test.Test
import kotlin.test.assertEquals

private fun impressionTestTree() =
    ColumnComponent(
        id = "root",
        children =
            listOf(
                TextComponent(id = "t1", text = "Hello"),
                TextComponent(id = "t2", text = "World"),
            ),
    )

@OptIn(ExperimentalTestApi::class)
class ImpressionTrackingTest {
    @Test
    fun `fires one impression per node on first composition, not on every recomposition`() =
        runDesktopComposeUiTest {
            val recorded = mutableListOf<AnalyticsEvent>()
            val tracker = AnalyticsTracker { recorded += it }
            val trackedRegistry =
                KompotRegistry((kompotCoreRenderers + kompotStandardRenderers).withImpressionTracking(tracker, KompotEventNamingRegistry()))

            setContent {
                TestKompotTheme {
                    CompositionLocalProvider(LocalKompotRegistry provides trackedRegistry) {
                        var tick by remember { mutableStateOf(0) }
                        Column {
                            Button(onClick = { tick++ }) { Text("tick: $tick") }
                            trackedRegistry.RenderNode(
                                component = impressionTestTree(),
                                actionHandler = recordingActionHandler(),
                                formController = testFormController(),
                            )
                        }
                    }
                }
            }

            waitForIdle()
            // root column + 2 text children = 3 impressions
            assertEquals(3, recorded.size)

            onNodeWithText("tick: 0").performClick()
            waitForIdle()

            assertEquals(3, recorded.size)
        }

    @Test
    fun `leaving and re-entering composition fires a new impression for the same node id`() =
        runDesktopComposeUiTest {
            val recorded = mutableListOf<AnalyticsEvent>()
            val tracker = AnalyticsTracker { recorded += it }
            val trackedRegistry =
                KompotRegistry((kompotCoreRenderers + kompotStandardRenderers).withImpressionTracking(tracker, KompotEventNamingRegistry()))

            setContent {
                TestKompotTheme {
                    CompositionLocalProvider(LocalKompotRegistry provides trackedRegistry) {
                        var visible by remember { mutableStateOf(true) }
                        Column {
                            Button(onClick = { visible = !visible }) { Text("toggle") }
                            if (visible) {
                                trackedRegistry.RenderNode(
                                    component = impressionTestTree(),
                                    actionHandler = recordingActionHandler(),
                                    formController = testFormController(),
                                )
                            }
                        }
                    }
                }
            }

            waitForIdle()
            assertEquals(3, recorded.size)

                onNodeWithText("toggle").performClick() // unmounted
            waitForIdle()
                onNodeWithText("toggle").performClick() // mounted again
            waitForIdle()

            assertEquals(6, recorded.size)
        }
}
