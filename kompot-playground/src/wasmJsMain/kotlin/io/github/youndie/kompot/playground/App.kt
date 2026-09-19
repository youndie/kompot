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
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.youndie.kompot.KompotRegistry
import io.github.youndie.kompot.ds.material.Material3DesignSystem
import io.github.youndie.kompot.kompotCoreRenderers
import io.github.youndie.kompot.kompotJson
import io.github.youndie.kompot.kompotStandardRenderers
import io.github.youndie.kompot.playground.demo.demoRenderers
import io.github.youndie.kompot.playground.demo.demoSerializersModule
import io.github.youndie.kompot.preview.KompotPreview
import io.github.youndie.kompot.preview.decodeKompotBody

// The page: the body on the left, the screen it becomes on the right.
//
// The registry is assembled exactly as a deployment assembles one — the core renderers, the standard
// set, and the deployment's own — because that is the claim the page makes: this is the client, not a
// drawing of it. `demoRenderers` is the playground's own plug-in (B-28), and it sits here in the same
// line a consumer would put theirs in.
private val registry = KompotRegistry(kompotCoreRenderers + kompotStandardRenderers + demoRenderers)

// ONE Json for both halves, and it carries the deployment's own types the way a client's does. The right pane decodes with whatever
// KompotPreview is given; the left pane decides whether the text is a body at all. Two instances
// would eventually disagree, and then the editor would call a body valid that the screen cannot draw
// — the exact confusion this page exists to remove. B-29 turns this single instance into two on
// purpose, which is a different thing entirely: there they model two CLIENTS.
private val json = kompotJson(demoSerializersModule)

@Composable
public fun PlaygroundApp() {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            // What is being typed, and what was last understood. They are the same string almost
            // always, and the whole reason for keeping two is the gap between them: a person adding a
            // property has an unparseable body between two keystrokes, and a page that blanked the
            // screen on every keystroke would be unusable exactly while it is being used.
            var text by remember { mutableStateOf(SAMPLE_BODY) }
            var drawn by remember { mutableStateOf(SAMPLE_BODY) }
            var failure by remember { mutableStateOf<String?>(null) }

            // The decode happens HERE, on the edit, rather than inside the composition of the right
            // pane: a throw during composition takes the whole page down, and a page that dies on a
            // missing brace tells a stranger the toolkit is broken when what broke is their comma.
            fun offer(next: String) {
                text = next
                val error = runCatching { json.decodeKompotBody(next) }.exceptionOrNull()
                failure = error?.let { it.message ?: it.toString() }
                if (error == null) drawn = next
            }

            Row(Modifier.fillMaxSize()) {
                BodyEditor(
                    text = text,
                    failure = failure,
                    onChange = ::offer,
                    modifier = Modifier.weight(BODY_WEIGHT).fillMaxHeight(),
                )

                VerticalDivider()

                ScreenPane(body = drawn, modifier = Modifier.weight(1f - BODY_WEIGHT).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun ScreenPane(
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("The screen a client draws", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)

        // Scrolls, because a body is as long as its author makes it and a clipped screen looks like a
        // renderer that lost the rest.
        Box(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
            KompotPreview(
                body = body,
                registry = registry,
                designSystem = Material3DesignSystem(),
                json = json,
                // NOT the default, and this is the one place the page must differ from a golden test.
                // KompotPreview fails loudly so that a screenshot never records a hole as the expected
                // picture; here a throw is a blank page in somebody's browser. The page reports and
                // carries on — B-29 turns this callback into the visible log.
                onDegraded = { kind, originalType -> println("[kompot] $kind: $originalType") },
            )
        }
    }
}

// Not a golden ratio and not a guess: the body is read in a monospace column where a long line is
// common, and the screen is the thing being looked at.
private const val BODY_WEIGHT = 0.42f
