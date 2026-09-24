package io.github.youndie.kompot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.RowComponent
import io.github.youndie.kompot.standard.TextComponent
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

// alignment and arrangement on row and column (SPEC.md §4.7). Every child here has a fixed size, so
// where it lands is a number to compare rather than a picture to look at: 300 dp of stack, children of
// 50 × 20 dp, and the expected position worked out from the rule, not read off the result.
@OptIn(ExperimentalTestApi::class)
class StackLayoutTest {
    private val renderers = KompotRegistry(kompotCoreRenderers + kompotStandardRenderers)

    private fun cell(label: String) =
        TextComponent(id = label, text = label, modifiers = listOf(KompotModifierNode.Size(widthDp = 50, heightDp = 20)))

    private fun row(
        arrangement: String? = null,
        alignment: String? = null,
        spacing: Int = 0,
        labels: List<String> = listOf("a", "b"),
        cellWidth: Int = 50,
    ) = RowComponent(
        id = "row",
        modifiers = listOf(KompotModifierNode.Size(widthDp = 300, heightDp = 100)),
        children = labels.map { TextComponent(id = it, text = it, modifiers = listOf(KompotModifierNode.Size(widthDp = cellWidth, heightDp = 20))) },
        spacing = spacing,
        arrangement = arrangement,
        alignment = alignment,
    )

    private fun layOut(
        node: KompotComponent,
        direction: LayoutDirection = LayoutDirection.Ltr,
        test: DesktopComposeUiTest.() -> Unit,
    ) = runDesktopComposeUiTest(width = 400, height = 400) {
        setContent {
            TestKompotTheme {
                CompositionLocalProvider(LocalKompotRegistry provides renderers, LocalLayoutDirection provides direction) {
                    Box(Modifier.size(400.dp, 400.dp)) {
                        renderers.RenderNode(node, recordingActionHandler(), testFormController())
                    }
                }
            }
        }
        test()
    }

    private fun DesktopComposeUiTest.left(label: String): Dp = onNodeWithText(label).getUnclippedBoundsInRoot().left

    private fun DesktopComposeUiTest.right(label: String): Dp = onNodeWithText(label).getUnclippedBoundsInRoot().right

    private fun DesktopComposeUiTest.top(label: String): Dp = onNodeWithText(label).getUnclippedBoundsInRoot().top

    private fun near(
        expected: Dp,
        actual: Dp,
        what: String,
    ) = assertTrue(abs((expected - actual).value) <= 1f, "$what: expected $expected, was $actual")

    @Test
    fun `without the words a row packs at the start, as it always did`() =
        layOut(row(spacing = 10)) {
            near(0.dp, left("a"), "first child")
            near(60.dp, left("b"), "second child, one spacing later")
            near(0.dp, top("a"), "cross axis defaults to the top")
        }

    @Test
    fun `space_between puts the ends at the edges`() =
        layOut(row(arrangement = "space_between", spacing = 10)) {
            near(0.dp, left("a"), "first child")
            near(300.dp, right("b"), "last child")
        }

    @Test
    fun `center and end move the packed group, keeping the spacing`() {
        // 50 + 10 + 50 = 110 of 300: 190 free.
        layOut(row(arrangement = "center", spacing = 10)) {
            near(95.dp, left("a"), "centred group")
            near(155.dp, left("b"), "second child, one spacing after the first")
        }
        layOut(row(arrangement = "end", spacing = 10)) {
            near(300.dp, right("b"), "last child at the far edge")
            near(190.dp, left("a"), "first child, one spacing before it")
        }
    }

    @Test
    fun `space_around and space_evenly share the free space as their names say`() {
        // 200 free of 300, two children, no spacing.
        layOut(row(arrangement = "space_around")) {
            // 100 per child, half of it on each side of it.
            near(50.dp, left("a"), "space_around, first")
            near(200.dp, left("b"), "space_around, second")
        }
        layOut(row(arrangement = "space_evenly")) {
            // Three equal gaps of 66.7.
            near(66.7.dp, left("a"), "space_evenly, first")
            near(183.3.dp, left("b"), "space_evenly, second")
        }
    }

    @Test
    fun `spacing stays the smallest gap when there is space to share`() =
        // 3 × 50 + 2 × 30 = 210 of 300: 90 free, 45 more per gap on top of the 30.
        layOut(row(arrangement = "space_between", spacing = 30, labels = listOf("a", "b", "c"))) {
            near(0.dp, left("a"), "first")
            near(125.dp, left("b"), "second: 50 + 30 + 45")
            near(300.dp, right("c"), "last at the edge")
        }

    @Test
    fun `content that does not fit keeps exactly the spacing, packed at the start`() =
        // 3 × 100 + 2 × 10 = 320 of 300: nothing to share, and the gap must not shrink below spacing.
        layOut(row(arrangement = "space_between", spacing = 10, labels = listOf("a", "b", "c"), cellWidth = 100)) {
            near(0.dp, left("a"), "first")
            near(110.dp, left("b"), "second, exactly one spacing after the first")
        }

    @Test
    fun `an unfamiliar word means the default`() =
        layOut(row(arrangement = "justify", alignment = "baseline", spacing = 10)) {
            near(0.dp, left("a"), "arrangement falls back to start")
            near(0.dp, top("a"), "alignment falls back to the top")
        }

    @Test
    fun `alignment moves a row's children across it`() {
        // A 100 dp tall row, children 20 dp tall.
        layOut(row(alignment = "center")) { near(40.dp, top("a"), "centred vertically") }
        layOut(row(alignment = "end")) { near(80.dp, top("a"), "at the bottom") }
    }

    @Test
    fun `alignment moves a column's children across it, arrangement along it`() =
        layOut(
            ColumnComponent(
                id = "column",
                modifiers = listOf(KompotModifierNode.Size(widthDp = 300, heightDp = 300)),
                children = listOf(cell("a"), cell("b")),
                alignment = "end",
                arrangement = "space_between",
            ),
        ) {
            near(250.dp, left("a"), "right-aligned in a 300 wide column")
            near(0.dp, top("a"), "first at the top")
            near(280.dp, top("b"), "last at the bottom: 300 - 20")
        }

    @Test
    fun `right to left, a row starts at the right edge`() =
        // The 400 dp box starts at the right too, so the 300 dp row occupies 100..400 of the window.
        layOut(row(arrangement = "start", spacing = 10), direction = LayoutDirection.Rtl) {
            near(400.dp, right("a"), "the first child is the rightmost")
            near(340.dp, right("b"), "the second, one spacing to its left")
        }
}
