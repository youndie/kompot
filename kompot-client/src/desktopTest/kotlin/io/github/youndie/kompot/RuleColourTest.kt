package io.github.youndie.kompot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import io.github.youndie.kompot.standard.DividerComponent
import io.github.youndie.kompot.standard.TableComponent
import io.github.youndie.kompot.standard.TableRow
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals

// A rule nobody coloured — a divider without a token, the table's border and row lines, and the
// table's header fill — used to be Material's whatever the design system was (#204). The design
// system now answers through two surface roles, and one that does not answer keeps Material's.
@OptIn(ExperimentalTestApi::class)
class RuleColourTest {
    private val line = Color(0xFFD32F2F)
    private val headerFill = Color(0xFF1565C0)
    private val headerInk = Color(0xFFFFEB3B)

    private class RulesDesignSystem(
        private val surfaces: Map<SurfaceRole, KompotSurface>,
    ) : KompotDesignSystem {
        @Composable
        override fun resolveColor(token: ColorToken): Color = Color.Black

        @Composable
        override fun resolveTypography(token: TypographyToken): TextStyle = TextStyle.Default

        @Composable
        override fun resolveSurface(role: SurfaceRole): KompotSurface = surfaces[role] ?: KompotSurface()
    }

    private val answering =
        RulesDesignSystem(
            mapOf(
                KompotSurfaceRoles.Divider to KompotSurface(outline = line),
                KompotSurfaceRoles.TableHeader to KompotSurface(container = headerFill, content = headerInk),
            ),
        )

    private val table =
        TableComponent(
            id = "table",
            rows =
                listOf(
                    TableRow(listOf("Head"), header = true),
                    TableRow(listOf("Body")),
                ),
        )

    private class Scene(
        val pixels: PixelMap,
        val divider: Rect,
        val table: Rect,
        val head: Rect,
        val material: MaterialColours,
    )

    private class MaterialColours(
        val outlineVariant: Color,
        val surfaceVariant: Color,
    )

    private fun drawn(
        designSystem: KompotDesignSystem,
        check: Scene.() -> Unit,
    ) = runDesktopComposeUiTest(width = 300, height = 300) {
        var material: MaterialColours? = null
        setContent {
            MaterialTheme {
                material =
                    MaterialColours(MaterialTheme.colorScheme.outlineVariant, MaterialTheme.colorScheme.surfaceVariant)
                CompositionLocalProvider(
                    LocalKompotDesignSystem provides designSystem,
                    LocalKompotRegistry provides KompotRegistry(emptyMap()),
                ) {
                    Column(Modifier.width(200.dp)) {
                        Box(Modifier.testTag("divider")) {
                            DividerRenderer().Render(
                                DividerComponent(id = "rule"),
                                recordingActionHandler(),
                                testFormController(),
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                        Box(Modifier.testTag("table")) {
                            TableRenderer().Render(table, recordingActionHandler(), testFormController())
                        }
                    }
                }
            }
        }
        Scene(
            pixels = onRoot().captureToImage().toPixelMap(),
            divider = boundsOf { onNodeWithTag("divider").getUnclippedBoundsInRoot() },
            table = boundsOf { onNodeWithTag("table").getUnclippedBoundsInRoot() },
            head = boundsOf { onNodeWithText("Head").getUnclippedBoundsInRoot() },
            material = material!!,
        ).check()
    }

    private fun DesktopComposeUiTest.boundsOf(read: () -> androidx.compose.ui.unit.DpRect): Rect {
        val dp = read()
        return with(density) { Rect(dp.left.toPx(), dp.top.toPx(), dp.right.toPx(), dp.bottom.toPx()) }
    }

    private fun Scene.at(
        x: Float,
        y: Float,
    ): Color = pixels[x.roundToInt(), y.roundToInt()]

    // The middle of each 1 dp line, and a point of the header fill clear of its words: the cell pads
    // its text by 8 dp, so the space just above the text is the row's own fill.
    private fun Scene.dividerPixel() = at(divider.center.x, divider.top)

    private fun Scene.borderPixel() = at(table.left, table.center.y)

    private fun Scene.rowRulePixel() = at(table.center.x, head.bottom + 8f)

    private fun Scene.headerFillPixel() = at(table.center.x, head.top - 4f)

    @Test
    fun `a design system that answers draws the divider and the table rules in its line`() =
        drawn(answering) {
            assertEquals(line, dividerPixel(), "the divider")
            assertEquals(line, borderPixel(), "the table's border")
            assertEquals(line, rowRulePixel(), "the rule between the table's rows")
        }

    @Test
    fun `a design system that answers fills the table header and inks its words`() =
        drawn(answering) {
            assertEquals(headerFill, headerFillPixel(), "the header row's fill")
            val ink =
                (head.left.roundToInt()..head.right.roundToInt()).flatMap { x ->
                    (head.top.roundToInt()..head.bottom.roundToInt()).map { y -> pixels[x, y] }
                }
            assertEquals(true, headerInk in ink, "no pixel of the header's words is in the role's ink")
        }

    // The control and the compatibility promise at once: a design system written before the roles
    // draws every rule exactly as it did.
    @Test
    fun `a design system that does not answer keeps Material's colours`() =
        drawn(RulesDesignSystem(emptyMap())) {
            assertEquals(material.outlineVariant, dividerPixel(), "the divider")
            assertEquals(material.outlineVariant, borderPixel(), "the table's border")
            assertEquals(material.outlineVariant, rowRulePixel(), "the rule between the table's rows")
            assertEquals(material.surfaceVariant, headerFillPixel(), "the header row's fill")
        }
}
