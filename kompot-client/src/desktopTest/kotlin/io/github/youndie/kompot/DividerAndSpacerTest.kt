package io.github.youndie.kompot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.DividerComponent
import io.github.youndie.kompot.standard.RowComponent
import io.github.youndie.kompot.standard.SpacerComponent
import io.github.youndie.kompot.standard.TextComponent
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

// divider and spacer (SPEC.md §4.10): both mean something only against the axis of the stack they are
// in. Fixed-size neighbours, positions worked out from the rule.
@OptIn(ExperimentalTestApi::class)
class DividerAndSpacerTest {
    // The divider has no text to find it by, so this registry draws it inside a tagged box — the
    // renderer itself unchanged, only found.
    private val renderers =
        KompotRegistry(
            kompotCoreRenderers + kompotStandardRenderers +
                mapOf(
                    DividerComponent::class to
                        object : KompotComponentRenderer<DividerComponent> {
                            @androidx.compose.runtime.Composable
                            override fun Render(
                                component: DividerComponent,
                                actionHandler: KompotActionHandler,
                                formController: io.github.youndie.kompot.form.FormController,
                            ) = Box(Modifier.testTag(component.id)) { DividerRenderer().Render(component, actionHandler, formController) }
                        },
                ),
        )

    private fun leaf(
        label: String,
        width: Int = 50,
        height: Int = 20,
    ) = TextComponent(id = label, text = label, modifiers = listOf(KompotModifierNode.Size(widthDp = width, heightDp = height)))

    private val rule = DividerComponent(id = "rule")

    private fun layOut(
        node: KompotComponent,
        scrolling: Boolean = false,
        test: DesktopComposeUiTest.() -> Unit,
    ) = runDesktopComposeUiTest(width = 400, height = 400) {
        setContent {
            TestKompotTheme {
                CompositionLocalProvider(LocalKompotRegistry provides renderers) {
                    val host = if (scrolling) Modifier.size(400.dp, 400.dp).verticalScroll(rememberScrollState()) else Modifier.size(400.dp, 400.dp)
                    Column(host) {
                        renderers.RenderNode(node, recordingActionHandler(), testFormController())
                    }
                }
            }
        }
        test()
    }

    private fun DesktopComposeUiTest.bounds(label: String) = onNodeWithText(label).getUnclippedBoundsInRoot()

    private fun near(
        expected: Dp,
        actual: Dp,
        what: String,
    ) = assertTrue(abs((expected - actual).value) <= 1f, "$what: expected $expected, was $actual")

    @Test
    fun `a spacer in a column is room below, a spacer in a row room beside`() {
        layOut(ColumnComponent(id = "c", children = listOf(leaf("a"), SpacerComponent(id = "s", size = 24), leaf("b")))) {
            near(44.dp, bounds("b").top, "20 of a, then 24 of room")
        }
        layOut(RowComponent(id = "r", children = listOf(leaf("a"), SpacerComponent(id = "s", size = 24), leaf("b")))) {
            near(74.dp, bounds("b").left, "50 of a, then 24 of room")
            near(0.dp, bounds("b").top, "and no room below")
        }
    }

    @Test
    fun `a weighted spacer pushes what follows to the far edge`() =
        layOut(
            RowComponent(
                id = "r",
                modifiers = listOf(KompotModifierNode.Size(widthDp = 300)),
                children = listOf(leaf("a"), SpacerComponent(id = "s", modifiers = listOf(KompotModifierNode.Weight(1f))), leaf("b")),
            ),
        ) {
            near(300.dp, bounds("b").right, "b at the right edge of the 300 dp row")
        }

    @Test
    fun `a divider in a column is a rule across it, between its neighbours`() =
        layOut(
            ColumnComponent(
                id = "c",
                modifiers = listOf(KompotModifierNode.Size(widthDp = 300)),
                children = listOf(leaf("a"), rule, leaf("b")),
            ),
        ) {
            // Material's divider is 1 dp thick: b starts one rule below a.
            near(21.dp, bounds("b").top, "b one rule below a")
            val divider = onNodeWithTag("rule").getUnclippedBoundsInRoot()
            near(300.dp, divider.right - divider.left, "the rule spans the column")
        }

    // The case the intrinsic height exists for: inside a scrolling page a row is offered unbounded
    // height, and a divider that fills it would otherwise be as tall as nothing.
    @Test
    fun `a divider in a row is a rule as tall as the row, even on a page that scrolls`() =
        layOut(
            RowComponent(id = "r", children = listOf(leaf("a", height = 40), rule, leaf("b", height = 20))),
            scrolling = true,
        ) {
            val divider = onNodeWithTag("rule").getUnclippedBoundsInRoot()
            near(40.dp, divider.bottom - divider.top, "as tall as the tallest neighbour")
            near(51.dp, bounds("b").left, "b one rule to the right of a")
        }
}
