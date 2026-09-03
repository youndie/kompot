package io.github.youndie.kompot.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.ui.component.Text

// The small segmented control the design uses inside tables and tab bars — 20 px tall, 12 px type
// — where Jewel's own is a toolbar-sized thing. One drawing for every "pick one of these" that
// sits inside a row.
@Composable
internal fun SmallSegmented(
    options: List<String>,
    selected: String,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    val colors = studioColors()
    Row(
        modifier.border(1.dp, colors.controlLine, RoundedCornerShape(6.dp)).padding(1.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        options.forEach { option ->
            val on = option == selected
            Text(
                option,
                Modifier
                    .background(if (on) colors.hover else Color.Transparent, RoundedCornerShape(4.dp))
                    .focusProperties { canFocus = false }
                    .clickable { onSelect(option) }
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                color = if (on) colors.text else colors.dim,
                fontSize = 12.sp,
            )
        }
    }
}
