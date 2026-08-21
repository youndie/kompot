package io.github.youndie.kompot

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.kompot.standard.TableComponent
import io.github.youndie.kompot.standard.TableRow
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class TableRendererTest {
    @Test
    fun `renders every cell of every row`() =
        runDesktopComposeUiTest {
            setContent {
                TestKompotTheme {
                    TableRenderer().Render(
                        component =
                            TableComponent(
                                id = "table",
                                rows =
                                    listOf(
                                        TableRow(listOf("Order type", "Standard discount", "Promo discount"), header = true),
                                        TableRow(listOf("From a template", "1%", "10%")),
                                        TableRow(listOf("Custom", "1%", "3%")),
                                    ),
                            ),
                        actionHandler = recordingActionHandler(),
                        formController = testFormController(),
                    )
                }
            }

            onNodeWithText("Order type").assertIsDisplayed()
            onNodeWithText("Standard discount").assertIsDisplayed()
            onNodeWithText("Promo discount").assertIsDisplayed()
            onNodeWithText("From a template").assertIsDisplayed()
            onAllNodesWithText("1%").assertCountEquals(2)
            onNodeWithText("10%").assertIsDisplayed()
            onNodeWithText("Custom").assertIsDisplayed()
            onNodeWithText("3%").assertIsDisplayed()
        }

    @Test
    fun `renders a single row table without crashing`() =
        runDesktopComposeUiTest {
            setContent {
                TestKompotTheme {
                    TableRenderer().Render(
                        component =
                            TableComponent(
                                id = "table",
                                rows = listOf(TableRow(listOf("Parameter", "Value"), header = true)),
                            ),
                        actionHandler = recordingActionHandler(),
                        formController = testFormController(),
                    )
                }
            }

            onAllNodesWithText("Parameter").assertCountEquals(1)
        }
}
