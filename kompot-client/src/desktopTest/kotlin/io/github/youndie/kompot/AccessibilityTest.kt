package io.github.youndie.kompot

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.kompot.standard.ButtonComponent
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.RowComponent
import io.github.youndie.kompot.standard.TextComponent
import io.github.youndie.kompot.standard.TextSpan
import kotlin.test.Test

// What a screen reader gets from a kompot screen (SPEC.md §4.11), read off the semantics tree — the
// tree assistive technology reads — rather than off the pixels.
@OptIn(ExperimentalTestApi::class)
class AccessibilityTest {
    private val renderers = KompotRegistry(kompotCoreRenderers + kompotStandardRenderers)
    private val open = NavigateAction(deeplink = "app://card/7")
    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
    private val isHeading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    private fun render(
        node: KompotComponent,
        test: DesktopComposeUiTest.() -> Unit,
    ) = runDesktopComposeUiTest {
        setContent {
            TestKompotTheme {
                CompositionLocalProvider(LocalKompotRegistry provides renderers) {
                    renderers.RenderNode(node, recordingActionHandler(), testFormController())
                }
            }
        }
        test()
    }

    private fun card(label: String? = null) =
        RowComponent(
            id = "card",
            children = listOf(TextComponent(id = "t", text = "Fix the login"), TextComponent(id = "d", text = "due Friday")),
            action = open,
            accessibilityLabel = label,
        )

    @Test
    fun `a row that opens something is a button, announced by the words the server gave`() =
        render(card(label = "Open card: Fix the login")) {
            onNode(isButton and hasClickAction() and hasContentDescription("Open card: Fix the login")).assertExists()
        }

    @Test
    fun `without a label the row is still a button, read by its merged children`() =
        render(card()) {
            onNode(isButton and hasText("Fix the login") and hasText("due Friday")).assertExists()
        }

    @Test
    fun `a row with no action is neither a button nor clickable`() =
        render(RowComponent(id = "r", children = listOf(TextComponent(id = "t", text = "Fix the login")))) {
            onAllNodes(isButton).assertCountEquals(0)
            onAllNodes(hasClickAction()).assertCountEquals(0)
        }

    @Test
    fun `a button can be announced by more than its words`() =
        render(ButtonComponent(id = "b", text = "×", action = open, accessibilityLabel = "Close the card")) {
            onNode(hasClickAction() and hasContentDescription("Close the card")).assertExists()
        }

    @Test
    fun `a heading is a heading, plain or in runs, and ordinary text is not`() {
        render(TextComponent(id = "h", text = "Today", heading = true)) {
            onNode(isHeading and hasText("Today")).assertExists()
        }
        render(TextComponent(id = "h", text = "Today, 3 cards", heading = true, spans = listOf(TextSpan(text = "Today, 3 cards")))) {
            onNode(isHeading and hasText("Today, 3 cards")).assertExists()
        }
        render(TextComponent(id = "p", text = "A paragraph")) {
            onAllNodes(isHeading).assertCountEquals(0)
        }
    }
}
