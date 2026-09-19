package io.github.youndie.kompot.playground

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Which body is on the table. Chips rather than a dropdown for one reason: the names ARE the tour —
// a reader who takes nothing else away from the page has read three sentences about what a body can
// be.
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExamplePicker(
    current: Example,
    onPick: (Example) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EXAMPLES.forEach { candidate ->
            FilterChip(
                selected = candidate == current,
                onClick = { onPick(candidate) },
                label = { Text(candidate.name) },
            )
        }
    }
}
