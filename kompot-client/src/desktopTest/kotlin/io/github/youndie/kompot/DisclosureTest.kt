package io.github.youndie.kompot

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.ExpandableComponent
import io.github.youndie.kompot.standard.TabsComponent
import io.github.youndie.kompot.standard.TabsItem
import io.github.youndie.kompot.standard.TextComponent
import kotlin.test.Test
import kotlin.test.assertEquals

// tabs and expandable (SPEC.md §4.12): the screen changes on the client, without an action reaching the
// application, and the value on the wire is where the reader STARTS — a later version of the node with
// the same value leaves their choice alone, a different value replaces it.
@OptIn(ExperimentalTestApi::class)
class DisclosureTest {
    private val raised = mutableListOf<KompotAction>()
    private val registry = KompotRegistry(kompotCoreRenderers + kompotStandardRenderers)

    private fun text(id: String) = TextComponent(id = id, text = id)

    private fun tabs(selected: Int = 0) =
        TabsComponent(
            id = "settings",
            tabs =
                listOf(
                    TabsItem("Profile", text("profile pane")),
                    TabsItem("Security", text("security pane")),
                    TabsItem("Billing", text("billing pane")),
                ),
            selected = selected,
        )

    private fun section(expanded: Boolean = false) =
        ExpandableComponent(id = "faq", header = text("What is kompot?"), content = text("the answer"), expanded = expanded)

    // The tree is state here so that a test can send the SAME node again — a new instance, equal or not —
    // which is how a navigate to the same screen and a refresh arrive.
    private var tree: KompotComponent by mutableStateOf(ColumnComponent(id = "root", children = emptyList()))

    private fun DesktopComposeUiTest.screen(first: KompotComponent) {
        tree = first
        setContent {
            TestKompotTheme {
                CompositionLocalProvider(LocalKompotRegistry provides registry) {
                    registry.RenderNode(tree, recordingActionHandler { raised += it }, testFormController())
                }
            }
        }
        waitForIdle()
    }

    private fun DesktopComposeUiTest.arrives(next: KompotComponent) {
        runOnIdle { tree = next }
        waitForIdle()
    }

    private fun DesktopComposeUiTest.shows(pane: String) {
        onNodeWithText(pane).assertExists()
        listOf("profile pane", "security pane", "billing pane").filter { it != pane }.forEach { onNodeWithText(it).assertDoesNotExist() }
    }

    @Test
    fun `tabs open on the selected one and switch without an action`() =
        runDesktopComposeUiTest {
            screen(tabs(selected = 1))
            shows("security pane")

            onNodeWithText("Billing").performClick()
            waitForIdle()
            shows("billing pane")
            assertEquals(emptyList(), raised, "switching a tab asks nobody")
        }

    @Test
    fun `the same value sent again keeps the reader's tab and a different one replaces it`() =
        runDesktopComposeUiTest {
            screen(tabs(selected = 0))
            onNodeWithText("Billing").performClick()
            waitForIdle()

            arrives(tabs(selected = 0))
            shows("billing pane")

            arrives(tabs(selected = 1))
            shows("security pane")
        }

    @Test
    fun `a live frame repeating the value keeps the reader's tab`() =
        runDesktopComposeUiTest {
            var frames by mutableStateOf(emptyMap<String, KompotComponent>())
            setContent {
                TestKompotTheme {
                    CompositionLocalProvider(LocalKompotRegistry provides registry, LocalKompotRealtimeUpdates provides frames) {
                        registry.RenderNode(tabs(selected = 0), recordingActionHandler(), testFormController())
                    }
                }
            }
            onNodeWithText("Security").performClick()
            waitForIdle()

            runOnIdle { frames = mapOf("settings" to tabs(selected = 0)) }
            waitForIdle()
            shows("security pane")
        }

    @Test
    fun `a selected index outside the list opens the first tab`() =
        runDesktopComposeUiTest {
            screen(tabs(selected = 7))
            shows("profile pane")
        }

    @Test
    fun `a tab is announced as a selected tab`() =
        runDesktopComposeUiTest {
            screen(tabs(selected = 1))
            onNodeWithText("Security").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assertIsSelected()
        }

    @Test
    fun `a section opens and closes from its header without an action`() =
        runDesktopComposeUiTest {
            screen(section())
            onNodeWithText("the answer").assertDoesNotExist()

            onNodeWithText("What is kompot?").performClick()
            waitForIdle()
            onNodeWithText("the answer").assertExists()

            onNodeWithText("What is kompot?").performClick()
            waitForIdle()
            onNodeWithText("the answer").assertDoesNotExist()
            assertEquals(emptyList(), raised)
        }

    @Test
    fun `a reload with the same value keeps an opened section open`() =
        runDesktopComposeUiTest {
            screen(section(expanded = false))
            onNodeWithText("What is kompot?").performClick()
            waitForIdle()

            arrives(section(expanded = false))
            onNodeWithText("the answer").assertExists()

            arrives(section(expanded = true))
            arrives(section(expanded = false))
            onNodeWithText("the answer").assertDoesNotExist()
        }

    // The header is a button that says whether it will expand or collapse — the platform's words for a
    // disclosure, not a label of ours.
    @Test
    fun `the header is a button offering expand, then collapse`() =
        runDesktopComposeUiTest {
            screen(section())
            val header = onNodeWithText("What is kompot?", useUnmergedTree = false)
            val button = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
            header.assert(button).assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand))
            header.assert(SemanticsMatcher.keyNotDefined(SemanticsActions.Collapse))

            header.performClick()
            waitForIdle()
            onNodeWithText("What is kompot?").assert(SemanticsMatcher.keyIsDefined(SemanticsActions.Collapse))
            onNodeWithText("What is kompot?").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.Expand))
        }
}
