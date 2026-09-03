package io.github.youndie.kompot.studio.palette

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.unit.dp
import io.github.youndie.kompot.studio.KompotStudioConfig
import io.github.youndie.kompot.studio.ui.EmptyState
import io.github.youndie.kompot.studio.ui.Icon
import io.github.youndie.kompot.studio.ui.StudioIcon
import io.github.youndie.kompot.studio.ui.studioColors
import org.jetbrains.jewel.ui.component.Text

// THE TYPES A SCREEN CAN BE BUILT FROM, taken from the profile and grouped by the module each came
// from. Nothing here is a hand-kept list: a deployment that adds a module sees its types appear, and
// one that removes it sees them go.
@Composable
internal fun PalettePane(
    config: KompotStudioConfig,
    modifier: Modifier = Modifier,
    onAdd: (String) -> Unit,
    dragModifier: (String) -> Modifier = { Modifier },
) {
    val colors = studioColors()
    val entries = remember(config) { paletteFor(config) }
    if (entries.isEmpty()) {
        // No profile means no closed list, and inventing one from the samples would offer a palette
        // that is silently a third of the truth.
        EmptyState(
            StudioIcon.MODULE,
            "No build profile",
            "The palette lists the types this build accepts, and a profile is what says which.",
            modifier,
        )
        return
    }

    val grouped = remember(entries) { entries.groupBy { it.group }.toList() }
    val open = remember(grouped) { mutableStateMapOf(*grouped.map { it.first to true }.toTypedArray()) }

    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 6.dp)) {
        grouped.forEach { (group, types) ->
            item(key = "group:$group") {
                val expanded = open[group] == true
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(ROW_HEIGHT)
                        .focusProperties { canFocus = false }
                        .clickable { open[group] = !expanded }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(if (expanded) StudioIcon.CHEVRON_DOWN else StudioIcon.CHEVRON_RIGHT, colors.dim)
                    Icon(StudioIcon.MODULE, colors.dim)
                    Text(group, color = colors.text)
                    Spacer(Modifier.weight(1f))
                    Text(types.size.toString(), color = colors.dim)
                }
            }
            if (open[group] == true) {
                items(types, key = { "type:${it.wireType}" }) { entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(ROW_HEIGHT)
                            .focusProperties { canFocus = false }
                            .clickable { onAdd(entry.wireType) }
                            .then(dragModifier(entry.wireType))
                            .padding(start = 30.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // A type WITHOUT a sample is marked rather than hidden: it can still be added,
                        // it will arrive close to empty, and that is exactly what a person needs
                        // warned about before they wonder why their new node draws nothing.
                        Icon(if (entry.hasSample) StudioIcon.WITH_SAMPLE else StudioIcon.NO_SAMPLE, colors.dim)
                        Text(entry.wireType, color = colors.text)
                    }
                }
            }
        }
    }
}

private val ROW_HEIGHT = 24.dp
