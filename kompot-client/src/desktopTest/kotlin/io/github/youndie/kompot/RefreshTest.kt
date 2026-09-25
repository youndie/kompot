package io.github.youndie.kompot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.RefreshAction
import io.github.youndie.kompot.standard.TextComponent
import kotlin.test.Test
import kotlin.test.assertEquals

// refresh (SPEC.md §16.4): the application reloads the screen it shows and hands the new tree to the
// same screen. What the toolkit owes it is §4.4 — state under stable ids survives — so a person who
// scrolled to row 30 is still at row 30, now showing what the server says row 30 is.
@OptIn(ExperimentalTestApi::class)
class RefreshTest {
    private val noPages =
        object : io.github.youndie.kompot.standard.KompotPageLoader {
            override suspend fun loadPage(
                url: String,
                params: Map<String, String>,
            ): io.github.youndie.kompot.standard.KompotPageResponse = error("no list on this screen")
        }

    private fun screen(version: Int) =
        ColumnComponent(
            id = "board",
            children = (0 until 40).map { TextComponent(id = "row$it", text = "row $it v$version") },
        )

    @Test
    fun `a refresh shows the new tree where the person was, and reloads once`() =
        runDesktopComposeUiTest(width = 400, height = 300) {
            var reloads = 0
            setContent {
                var tree by remember { mutableStateOf(screen(1)) }
                val scope = rememberCoroutineScope()
                val handler =
                    remember {
                        KompotActionHandler {}.withRefresh(scope) {
                            reloads++
                            tree = screen(2)
                        }
                    }
                TestKompotTheme {
                    // No list here, so no page is ever asked for; the screen still requires a loader.
                    androidx.compose.runtime.CompositionLocalProvider(LocalKompotPageLoader provides noPages) {
                    Column {
                        Button(onClick = { handler.handle(RefreshAction) }) { Text("refresh") }
                        KompotLazyScreen(
                            rootComponent = tree,
                            registry = KompotRegistry(kompotCoreRenderers + kompotStandardRenderers),
                            formController = testFormController(),
                            actionHandler = handler,
                            modifier = Modifier.fillMaxWidth().height(200.dp).testTag("screen"),
                        )
                    }
                    }
                }
            }

            onNodeWithTag("screen").performScrollToIndex(30)
            waitForIdle()
            onNodeWithText("row 30 v1").assertIsDisplayed()

            onNodeWithText("refresh").performClick()
            waitForIdle()

            assertEquals(1, reloads)
            onNodeWithText("row 30 v2").assertIsDisplayed()
            onNodeWithText("row 0 v2").assertDoesNotExist()
        }
}
