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

// The lines between panels, in the control-outline colour rather than the design's border colour:
// a one-pixel line one step lighter than the panel disappears on any screen that is not the
// designer's, and a rule nobody can see separates nothing.
@Composable
internal fun HRule(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(studioColors().controlLine))
}

@Composable
internal fun VRule(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxHeight().width(1.dp).background(studioColors().controlLine))
}
