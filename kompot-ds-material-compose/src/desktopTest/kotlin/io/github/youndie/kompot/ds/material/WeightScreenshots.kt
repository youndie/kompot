package io.github.youndie.kompot.ds.material

import androidx.compose.runtime.Composable
import io.github.youndie.kompot.KompotModifierNode
import io.github.youndie.kompot.RowRenderer
import io.github.youndie.kompot.SizeType
import io.github.youndie.kompot.material3.M3Colors
import io.github.youndie.kompot.material3.M3Typography
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.RowComponent
import io.github.youndie.kompot.standard.TextComponent
import ru.workinprogress.viddik.annotations.ViddikScreenshot

// A weighted node reserves its share of the row — an empty one still pushes its siblings, which is how
// the spacer idiom works — and the question is whether it PAINTS that share. The picture answers it:
// the weighted child is given a background, so anything narrower than its share is visible at a glance.
//
// The trap is that it stays invisible while the data is long: text that wraps stretches itself to the
// constraint, so a screen with long titles looks right and the same tree with short ones does not.
private fun card(label: String) =
    ColumnComponent(
        id = "body_$label",
        modifiers =
            listOf(
                KompotModifierNode.Weight(1f),
                KompotModifierNode.Background(M3Colors.SecondaryContainer),
                KompotModifierNode.Padding(all = 8),
            ),
        children = listOf(TextComponent(id = "t_$label", text = label, style = M3Typography.BodyMedium)),
    )

private val WEIGHTED_CARD =
    RowComponent(
        id = "card",
        modifiers =
            listOf(
                KompotModifierNode.Size(width = SizeType.Fill),
                KompotModifierNode.Background(M3Colors.Background),
                KompotModifierNode.Padding(all = 8),
            ),
        spacing = 8,
        children =
            listOf(
                // The provenance stripe of the report: a fixed sibling the weighted body sits beside.
                ColumnComponent(
                    id = "stripe",
                    modifiers =
                        listOf(
                            KompotModifierNode.Size(height = SizeType.Fill, widthDp = 3),
                            KompotModifierNode.Background(M3Colors.Primary),
                        ),
                    children = emptyList(),
                ),
                card("Short"),
            ),
    )

@ViddikScreenshot(name = "Weight - a weighted node paints its share", group = "Renderer", width = 420, height = 90)
@Composable
fun WeightedNodePaintsItsShareScreenshot() {
    RendererScreenshotTheme {
        RowRenderer().Render(
            component = WEIGHTED_CARD,
            actionHandler = recordingActionHandler(),
            formController = testFormController(),
        )
    }
}
