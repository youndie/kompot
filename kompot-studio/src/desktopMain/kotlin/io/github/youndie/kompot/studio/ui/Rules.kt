package io.github.youndie.kompot.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// The lines between panels, in the studio's own line colour rather than Jewel's divider style: the
// theme's divider is tuned for the inside of a component, and between two panels of the same grey
// it disappears.
@Composable
internal fun HRule(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(studioColors().line))
}

@Composable
internal fun VRule(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxHeight().width(1.dp).background(studioColors().line))
}
