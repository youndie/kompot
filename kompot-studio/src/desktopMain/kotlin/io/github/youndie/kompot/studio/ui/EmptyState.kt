package io.github.youndie.kompot.studio.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.Text

// A panel with nothing in it says three things, in this order: what is missing, where it would come
// from, and — when there is one — the one move that fills it. The same shape everywhere, so an empty
// tree, an empty palette and an empty drawer are read the same way.
@Composable
internal fun EmptyState(
    icon: StudioIcon,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = studioColors()
    Column(modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, colors.dim)
            Text(title)
        }
        Text(detail, Modifier.padding(start = 24.dp), color = colors.dim)
        if (action != null && onAction != null) {
            Text(
                action,
                Modifier.padding(start = 24.dp).focusProperties { canFocus = false }.clickable(onClick = onAction),
                color = colors.accent,
            )
        }
    }
}
