package io.github.youndie.kompot.ds.material

import androidx.compose.runtime.Composable
import io.github.youndie.kompot.ColumnRenderer
import io.github.youndie.kompot.KompotModifierNode
import io.github.youndie.kompot.RowRenderer
import io.github.youndie.kompot.SizeType
import io.github.youndie.kompot.material3.M3Colors
import io.github.youndie.kompot.material3.M3Typography
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.RowComponent
import io.github.youndie.kompot.standard.TextComponent
import ru.workinprogress.viddik.annotations.ViddikScreenshot

// The three layout techniques issue #3 reported as inexpressible, in one shot: a one-dp rule used
// instead of a border, a two-dp stripe, and two panels of fixed width. Each of them is a number the
// protocol had no way to carry until widthDp/heightDp existed, so the golden is the evidence that
// the gap is closed — a compiling DSL call would only prove the server can say it.
private val FIXED_EXTENTS_TREE =
    ColumnComponent(
        id = "extents",
        modifiers =
            listOf(
                KompotModifierNode.Size(width = SizeType.Fill),
                KompotModifierNode.Background(M3Colors.Background),
                KompotModifierNode.Padding(all = 12),
            ),
        spacing = 12,
        children =
            listOf(
                TextComponent(id = "caption", text = "Fixed extents", style = M3Typography.TitleMedium),
                // A rule, not a border: full width, exactly one dp tall.
                ColumnComponent(
                    id = "rule",
                    modifiers =
                        listOf(
                            KompotModifierNode.Size(width = SizeType.Fill, heightDp = 1),
                            KompotModifierNode.Background(M3Colors.Outline),
                        ),
                    children = emptyList(),
                ),
                RowComponent(
                    id = "panels",
                    spacing = 8,
                    children =
                        listOf(
                            // The stripe: two dp wide, its height taken from the row.
                            ColumnComponent(
                                id = "stripe",
                                modifiers =
                                    listOf(
                                        KompotModifierNode.Size(height = SizeType.Fill, widthDp = 2),
                                        KompotModifierNode.Background(M3Colors.Primary),
                                    ),
                                children = emptyList(),
                            ),
                            ColumnComponent(
                                id = "wide_panel",
                                modifiers =
                                    listOf(
                                        KompotModifierNode.Size(widthDp = 150),
                                        KompotModifierNode.Background(M3Colors.SurfaceVariant),
                                        KompotModifierNode.Padding(all = 8),
                                    ),
                                children = listOf(TextComponent(id = "wide", text = "150 dp", style = M3Typography.BodyMedium)),
                            ),
                            ColumnComponent(
                                id = "narrow_panel",
                                modifiers =
                                    listOf(
                                        KompotModifierNode.Size(widthDp = 80),
                                        KompotModifierNode.Background(M3Colors.SecondaryContainer),
                                        KompotModifierNode.Padding(all = 8),
                                    ),
                                children = listOf(TextComponent(id = "narrow", text = "80 dp", style = M3Typography.BodyMedium)),
                            ),
                        ),
                )
            ),
    )

@ViddikScreenshot(name = "Size - fixed extents in dp", group = "Renderer", width = 420, height = 160)
@Composable
fun FixedExtentsScreenshot() {
    RendererScreenshotTheme {
        ColumnRenderer().Render(
            component = FIXED_EXTENTS_TREE,
            actionHandler = recordingActionHandler(),
            formController = testFormController(),
        )
    }
}

// A number and a symbol on the same axis contradict each other, and SPEC.md §5.4 resolves it in
// favour of the number. The two panels below carry Fill AND a width in dp; if the rule were the
// other way round they would each take half the row.
private val CONFLICT_TREE =
    RowComponent(
        id = "conflict",
        modifiers =
            listOf(
                KompotModifierNode.Size(width = SizeType.Fill),
                KompotModifierNode.Background(M3Colors.Background),
                KompotModifierNode.Padding(all = 12),
            ),
        spacing = 8,
        children =
            listOf(
                ColumnComponent(
                    id = "fill_and_number",
                    modifiers =
                        listOf(
                            KompotModifierNode.Size(width = SizeType.Fill, widthDp = 120),
                            KompotModifierNode.Background(M3Colors.SurfaceVariant),
                            KompotModifierNode.Padding(all = 8),
                        ),
                    children = listOf(TextComponent(id = "n1", text = "120 dp", style = M3Typography.BodyMedium)),
                ),
                ColumnComponent(
                    id = "fill_only",
                    modifiers =
                        listOf(
                            KompotModifierNode.Size(width = SizeType.Fill),
                            KompotModifierNode.Background(M3Colors.SecondaryContainer),
                            KompotModifierNode.Padding(all = 8),
                        ),
                    children = listOf(TextComponent(id = "n2", text = "Fill", style = M3Typography.BodyMedium)),
                ),
            ),
    )

@ViddikScreenshot(name = "Size - a number outranks Fill", group = "Renderer", width = 420, height = 90)
@Composable
fun NumberOutranksFillScreenshot() {
    RendererScreenshotTheme {
        RowRenderer().Render(
            component = CONFLICT_TREE,
            actionHandler = recordingActionHandler(),
            formController = testFormController(),
        )
    }
}
