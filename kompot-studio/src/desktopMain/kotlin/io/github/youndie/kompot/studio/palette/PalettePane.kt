package io.github.youndie.kompot.studio.palette

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.youndie.kompot.studio.KompotStudioConfig
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
    val entries = remember(config) { paletteFor(config) }
    if (entries.isEmpty()) {
        // No profile means no closed list, and inventing one from the samples would offer a palette
        // that is silently a third of the truth.
        Text("No profile: the palette needs one to know what this build accepts.", modifier.padding(8.dp))
        return
    }

    val grouped = remember(entries) { entries.groupBy { it.group }.toList() }

    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 8.dp)) {
        grouped.forEach { (group, types) ->
            item(key = "group:$group") {
                Text(group, Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
            items(types, key = { "type:${it.wireType}" }) { entry ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onAdd(entry.wireType) }
                        .then(dragModifier(entry.wireType))
                        .padding(start = 20.dp, top = 2.dp, bottom = 2.dp, end = 8.dp),
                ) {
                    // A type WITHOUT a sample is marked rather than hidden: it can still be added, it
                    // will arrive close to empty, and that is exactly what a person needs warned about
                    // before they wonder why their new node draws nothing.
                    Text(if (entry.hasSample) entry.wireType else "${entry.wireType} ·")
                }
            }
        }
    }
}
