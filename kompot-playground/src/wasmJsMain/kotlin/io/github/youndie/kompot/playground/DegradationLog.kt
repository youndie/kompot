package io.github.youndie.kompot.playground

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.youndie.kompot.KompotDegradationKind

// WHAT THE CLIENT DID NOT UNDERSTAND, in its own words.
//
// Without this the second state of the switch is an empty space, and an empty space is not a
// mechanism: a hole says nothing about itself, which is exactly why the toolkit grew a degradation
// sink in the first place. Here it is the difference between "the screen is broken" and "this node
// was unfamiliar and the screen survived it".
public class DegradationLog {
    private val entries = mutableStateListOf<String>()

    // The wire types this client did not understand, as the sink named them. The tree marks its nodes
    // from this rather than from a guess at the profile, so the two halves of the page cannot disagree
    // about what was unfamiliar.
    //
    // UNRENDERABLE_COMPONENT is deliberately not in here: that kind reports the KOTLIN class name
    // (B-34), which matches no "type" in the body, and a mark that silently never fires is worse than
    // no mark at all.
    private val unknown = mutableStateListOf<String>()

    public val lines: List<String> get() = entries

    public val unknownTypes: Set<String> get() = unknown.toSet()

    public fun clear() {
        entries.clear()
        unknown.clear()
    }

    // Called from inside composition, by the render pane, and read by a composable beside it. Appending
    // unconditionally would append again on every recomposition — including the one the append itself
    // causes — and the page would grow a line per frame for ever. Distinct entries make the write
    // idempotent, and that is also the honest shape of the thing: the log answers what this client
    // failed to understand, not how many times the pane was composed.
    public fun report(
        kind: KompotDegradationKind,
        originalType: String,
        drawnAsFallback: Boolean,
    ) {
        // The toolkit's own words, taken from the default sink in Degradation.kt rather than invented
        // here: what a deployment reads in its logs and what this page shows should be one sentence,
        // or the page teaches a vocabulary nobody else uses.
        val line =
            "$kind  \"$originalType\"" + if (drawnAsFallback) "  drawn through its fallback" else "  skipped"
        if (line !in entries) entries += line
        if (kind == KompotDegradationKind.UNKNOWN_COMPONENT && originalType !in unknown) unknown += originalType
    }
}

@Composable
internal fun DegradationLogPane(
    log: DegradationLog,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "What this client could not understand",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.outline,
        )

        Surface(
            color =
                if (log.lines.isEmpty()) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
            contentColor =
                if (log.lines.isEmpty()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                },
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (log.lines.isEmpty()) {
                    Text("Nothing — every type on the wire was known to it.", style = MaterialTheme.typography.bodySmall)
                } else {
                    log.lines.forEach { line ->
                        Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}
