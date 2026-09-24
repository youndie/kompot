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
import io.github.youndie.kompot.standard.BoxComponent
import io.github.youndie.kompot.standard.TextComponent
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

// box (SPEC.md §4.8): layers over one another. A 200 × 100 "picture" sets the box's size, and what is
// laid over it is placed by the box's alignment — or, for a layer that sits elsewhere, by a nested box
// that fills this one. Positions are worked out from the rule, inside a 400 × 400 window: the window is
// what a filling layer must NOT take.
@OptIn(ExperimentalTestApi::class)
class BoxLayoutTest {
    private val renderers = KompotRegistry(kompotCoreRenderers + kompotStandardRenderers)

    private fun leaf(
        label: String,
        width: Int,
        height: Int,
    ) = TextComponent(id = label, text = label, modifiers = listOf(KompotModifierNode.Size(widthDp = width, heightDp = height)))

    private val picture = leaf("picture", 200, 100)

    private fun filling(
        id: String,
        alignment: String,
        child: KompotComponent,
    ) = BoxComponent(
        id = id,
        modifiers = listOf(KompotModifierNode.Size(width = SizeType.Fill, height = SizeType.Fill)),
        children = listOf(child),
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

    private fun DesktopComposeUiTest.bounds(label: String) = onNodeWithText(label).getUnclippedBoundsInRoot()

    private fun near(
        expected: Dp,
        actual: Dp,
        what: String,
    ) = assertTrue(abs((expected - actual).value) <= 1f, "$what: expected $expected, was $actual")

    @Test
    fun `a badge is laid over the corner the box names`() =
        layOut(BoxComponent(id = "b", children = listOf(picture, leaf("badge", 30, 20)), alignment = "top_end")) {
            near(200.dp, bounds("picture").right, "the picture fills the box it sizes")
            near(200.dp, bounds("badge").right, "badge at the right edge of the picture")
            near(0.dp, bounds("badge").top, "badge at the top")
        }

    @Test
    fun `layers that fill take the picture's size, not the window's`() =
        layOut(
            BoxComponent(
                id = "card",
                children =
                    listOf(
                        picture,
                        filling("caption_frame", "bottom_start", leaf("caption", 80, 20)),
                        filling("badge_frame", "top_end", leaf("badge", 30, 20)),
                    ),
            ),
        ) {
            // Were the frames the window's size, the caption would sit at 380 and the badge at 400.
            near(80.dp, bounds("caption").top, "caption at the bottom of the 100 dp picture")
            near(0.dp, bounds("caption").left, "caption at the start")
            near(200.dp, bounds("badge").right, "badge at the right of the 200 dp picture")
            near(0.dp, bounds("badge").top, "badge at the top")
        }

    @Test
    fun `a layer that fills one axis takes the box's extent there and its own on the other`() =
        layOut(
            BoxComponent(
                id = "b",
                alignment = "bottom_start",
                children =
                    listOf(
                        picture,
                        TextComponent(
                            id = "strip",
                            text = "strip",
                            modifiers = listOf(KompotModifierNode.Size(width = SizeType.Fill, heightDp = 20)),
                        ),
                    ),
            ),
        ) {
            near(200.dp, bounds("strip").right - bounds("strip").left, "as wide as the picture")
            near(20.dp, bounds("strip").bottom - bounds("strip").top, "its own height")
            near(80.dp, bounds("strip").top, "at the bottom of the box")
        }

    @Test
    fun `an unfamiliar word means the top start`() =
        layOut(BoxComponent(id = "b", children = listOf(picture, leaf("badge", 30, 20)), alignment = "somewhere")) {
            near(0.dp, bounds("badge").left, "start")
            near(0.dp, bounds("badge").top, "top")
        }

    @Test
    fun `a box whose every layer fills takes what it is offered`() =
        layOut(BoxComponent(id = "b", children = listOf(filling("f", "center", leaf("dot", 20, 20))))) {
            near(190.dp, bounds("dot").left, "centred in the 400 dp window: (400 - 20) / 2")
            near(190.dp, bounds("dot").top, "and vertically")
        }

    @Test
    fun `right to left, the end is the left edge`() =
        layOut(
            BoxComponent(id = "b", children = listOf(picture, leaf("badge", 30, 20)), alignment = "top_end"),
            direction = LayoutDirection.Rtl,
        ) {
            // The 400 dp window starts at the right, so the 200 dp box occupies 200..400.
            near(200.dp, bounds("badge").left, "end is the left edge of the box")
        }
}
