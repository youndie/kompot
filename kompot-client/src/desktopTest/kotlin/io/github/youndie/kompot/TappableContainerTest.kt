package io.github.youndie.kompot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.RowComponent
import io.github.youndie.kompot.standard.TextComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The gesture a feed expects: tapping anywhere on a row opens the thing it describes. Before an
// action could sit on a container, a list of openable items had to be a list of buttons.
@OptIn(ExperimentalTestApi::class)
class TappableContainerTest {
    private val open = NavigateAction(deeplink = "app://entry/42")

    @Test
    fun `tapping anywhere on a row raises its action`() =
        runDesktopComposeUiTest {
            val raised = mutableListOf<KompotAction>()
            setContent {
                WithStandardRenderers {
                    RowRenderer().Render(
                        component =
                            RowComponent(
                                id = "entry",
                                children =
                                    listOf(
                                        TextComponent(id = "who", text = "Anna"),
                                        TextComponent(id = "what", text = "moved a task"),
                                    ),
                                action = open,
                            ),
                        actionHandler = recordingActionHandler { raised += it },
                        formController = testFormController(),
                    )
                }
            }

            // A child, not the container: the tap must reach the row THROUGH whatever it contains,
            // which is the difference between a tappable row and a row containing a button.
            onNodeWithText("moved a task").performClick()

            assertEquals(listOf<KompotAction>(open), raised)
        }

    @Test
    fun `a column carries an action the same way`() =
        runDesktopComposeUiTest {
            val raised = mutableListOf<KompotAction>()
            setContent {
                WithStandardRenderers {
                    ColumnRenderer().Render(
                        component = ColumnComponent(id = "card", children = listOf(TextComponent(id = "t", text = "Card")), action = open),
                        actionHandler = recordingActionHandler { raised += it },
                        formController = testFormController(),
                    )
                }
            }

            onNodeWithText("Card").performClick()

            assertEquals(listOf<KompotAction>(open), raised)
        }

    // Without an action a container must stay an ordinary container — no ripple, no click semantics,
    // and above all nothing raised. A clickable wrapping an empty lambda would pass a naive test and
    // fail this one.
    @Test
    fun `a container without an action raises nothing when tapped`() =
        runDesktopComposeUiTest {
            val raised = mutableListOf<KompotAction>()
            setContent {
                WithStandardRenderers {
                    RowRenderer().Render(
                        component = RowComponent(id = "plain", children = listOf(TextComponent(id = "t", text = "Just text"))),
                        actionHandler = recordingActionHandler { raised += it },
                        formController = testFormController(),
                    )
                }
            }

            onNodeWithText("Just text").performClick()

            assertTrue(raised.isEmpty(), raised.toString())
        }
}

// TestKompotTheme provides an EMPTY registry, and a container renders its children through the
// registry — so with that one the rows would come out blank and every assertion below would pass or
// fail for the wrong reason. Here the children have to be real, because the tap is delivered to a
// child and must reach the container through it.
@Composable
private fun WithStandardRenderers(content: @Composable () -> Unit) {
    TestKompotTheme {
        CompositionLocalProvider(
            LocalKompotRegistry provides KompotRegistry(kompotCoreRenderers + kompotStandardRenderers),
            content = content,
        )
    }
}
