package io.github.youndie.kompot.playground

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.youndie.kompot.KompotDegradationSink
import io.github.youndie.kompot.ds.material.Material3DesignSystem
import io.github.youndie.kompot.preview.KompotPreview
import io.github.youndie.kompot.preview.decodeKompotBody

// The page: the body on the left, and on the right the client that reads it — which client is the
// switch, and what it could not understand is the log underneath.
//
// Nothing here assembles a registry or a Json of its own any more: both belong to a ClientMode, because
// "which client" is the question the page asks. See ClientSwitch.kt.
@Composable
public fun PlaygroundApp() {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            var mode by remember { mutableStateOf(ClientMode.CURRENT) }

            // What is being typed, and what was last understood. They are the same string almost
            // always, and the whole reason for keeping two is the gap between them: a person adding a
            // property has an unparseable body between two keystrokes, and a page that blanked the
            // screen on every keystroke would be unusable exactly while it is being used.
            var example by remember { mutableStateOf(EXAMPLES.first()) }
            var text by remember { mutableStateOf(example.body) }
            var drawn by remember { mutableStateOf(example.body) }
            var failure by remember { mutableStateOf<String?>(null) }

            val log = remember { DegradationLog() }

            // Which node the reader is pointing at. Null is a real state and the common one: the page
            // is read before it is poked.
            var selectedId by remember { mutableStateOf<String?>(null) }

            // The decode happens HERE, on the edit, rather than inside the composition of the right
            // pane: a throw during composition takes the whole page down, and a page that dies on a
            // missing brace tells a stranger the toolkit is broken when what broke is their comma.
            fun offer(next: String) {
                text = next
                val error = runCatching { mode.json.decodeKompotBody(next) }.exceptionOrNull()
                failure = error?.let { it.message ?: it.toString() }
                if (error == null) drawn = next
            }

            // The client changes, and with it one thing in the BODY: whether the server named an
            // equivalent. That half is not the client's to decide (SPEC.md §2.1), so it happens to the
            // text in the editor where it can be seen, rather than quietly inside the render.
            fun switchTo(next: ClientMode) {
                mode = next
                offer(text.withServerFallback(next.serverNamesFallback))
            }

            // Choosing an example replaces the body and keeps the client: which client is looking is
            // the reader's other axis, and losing it on every example would make the two impossible to
            // compare.
            fun show(next: Example) {
                example = next
                selectedId = null
                offer(next.body.withServerFallback(mode.serverNamesFallback))
            }

            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(BODY_WEIGHT).fillMaxHeight().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExamplePicker(current = example, onPick = ::show, modifier = Modifier.fillMaxWidth())

                    Text("The tree the body describes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)

                    // The tree walks the body that was DRAWN rather than the text being typed: a tree
                    // rebuilt from half-written JSON would flicker through shapes nobody sent.
                    BodyTree(
                        body = drawn,
                        unknownTypes = log.unknownTypes,
                        selectedId = selectedId,
                        onSelect = { selectedId = it },
                        modifier = Modifier.fillMaxWidth().weight(TREE_WEIGHT),
                    )

                    HorizontalDivider()

                    BodyEditor(
                        text = text,
                        failure = failure,
                        onChange = ::offer,
                        modifier = Modifier.fillMaxWidth().weight(1f - TREE_WEIGHT),
                    )
                }

                VerticalDivider()

                ClientPane(
                    mode = mode,
                    body = drawn,
                    log = log,
                    selectedId = selectedId,
                    onModeChange = ::switchTo,
                    modifier = Modifier.weight(1f - BODY_WEIGHT).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun ClientPane(
    mode: ClientMode,
    body: String,
    log: DegradationLog,
    selectedId: String?,
    onModeChange: (ClientMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ClientSwitch(mode = mode, onChange = onModeChange, modifier = Modifier.fillMaxWidth())

        Text("The screen this client draws", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)

        // Cleared HERE rather than in an effect, and the ordering is the whole reason: this runs before
        // the render pane below composes, while a LaunchedEffect would run after it — wiping the very
        // entries the sink had just reported.
        remember(mode, body) { log.clear() }

        // REMEMBERED, and not for speed: LocalKompotDegradationSink is a static composition local, so a
        // new instance on every composition invalidates the whole render subtree — with a sink that
        // writes state the pane beside it reads, that is a loop rather than a slowdown.
        val sink =
            remember(log) {
                KompotDegradationSink { kind, originalType, outcome -> log.report(kind, originalType, outcome) }
            }

        // Scrolls, because a body is as long as its author makes it and a clipped screen looks like a
        // renderer that lost the rest.
        Box(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
            KompotPreview(
                body = body,
                // Decorated, not replaced: the outline is added around the renderer the client really
                // has, so what is drawn inside the frame is still the client's own work.
                registry = remember(mode, selectedId) { mode.registry.outlining(selectedId) },
                designSystem = Material3DesignSystem(),
                json = mode.json,
                // The WHOLE sink rather than onDegraded, because the page's subject is the one fact
                // onDegraded drops: whether anything was drawn in the node's place. Without it the
                // second and third states of the switch report the same line.
                //
                // It also replaces the loud default: KompotPreview fails on degradation so that a
                // screenshot never records a hole as the expected picture, and here the hole IS the
                // subject — a throw would be a blank page in somebody's browser.
                degradationSink = sink,
            )
        }

        DegradationLogPane(log = log, modifier = Modifier.fillMaxWidth())
    }
}

// Not a golden ratio and not a guess: the body is read in a monospace column where a long line is
// common, and the screen is the thing being looked at.
private const val BODY_WEIGHT = 0.42f

// The tree is the shape of the body and the editor is its text; the shape is read more often than it
// is typed.
private const val TREE_WEIGHT = 0.45f
