package io.github.youndie.kompot.studio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.window.rememberWindowState
import com.jetbrains.JBR
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.intui.standalone.theme.darkThemeDefinition
import org.jetbrains.jewel.intui.standalone.theme.lightThemeDefinition
import org.jetbrains.jewel.intui.window.decoratedWindow
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.HorizontalSplitLayout
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextArea
import org.jetbrains.jewel.window.DecoratedWindow
import org.jetbrains.jewel.window.TitleBar
import org.jetbrains.jewel.window.newFullscreenControls

// B-08 — THE SPIKE, AND ITS FIVE ANSWERS.
//
// The research took three decisions on paper: that Jewel's shell and the toolkit's material3
// renderers live in one window, that KompotPreview draws inside a Jewel split without losing its
// MaterialTheme, and that Compose Hot Reload redraws a renderer edited in place. Each could be false
// for a reason only a running window shows — the way skiko's poisoned Surface class was, next door in
// IdePreviewExperiment.kt. The five questions, and what running this answered:
//
// (1) DOES THE WINDOW OPEN ON A JBR AND ON A PLAIN JDK? — YES to both, and the second only because
//     this file branches. Research §5.5 said DecoratedWindow "degrades to an ordinary window" on a
//     non-JetBrains runtime. It does not: its first statement is `if (!JBR.isAvailable()) error(...)`,
//     and without the branch below the studio would die at startup with a message about Jewel rather
//     than about itself. On a JBR the gate opens — JetBrainsRuntimeTest asserts exactly that call on
//     the runtime the build provisions, which is a check a machine with no screen can run; the plain
//     JDK path was watched opening a window on OpenJDK 25 before the toolchain was pinned.
//
// (2) IS A KOMPOT BUTTON DRAWN IN MaterialTheme's COLOURS OR JEWEL's? — Material's. Asserted rather
//     than looked at: SpikeCaptureTest reads SPIKE_PRIMARY out of a captured frame, and that colour
//     exists nowhere in the Jewel palette. The boundary is SpikeFrame — the render pane sits inside
//     the consumer's MaterialTheme, the chrome inside IntUiTheme, which is the boundary §5.2 needed
//     anyway.
//
// (3) DOES HOT RELOAD REDRAW AN EDITED RENDERER? — STILL OPEN, and this is the one answer the spike
//     does not have. Both preconditions are now in place and were not assumed: the Compose plugin
//     registers `hotRunDesktop`, `hotReloadDesktopMain` and `reload` for this module out of the box,
//     and `run` executes on a JetBrains Runtime. What is missing is the only part that cannot be
//     automated here — a session with a screen, where somebody edits ButtonRenderer and watches the
//     frame. Recorded as open rather than guessed at, and §5.5 of the research says so.
//
// (4) DOES captureComposable TAKE THE FRAME THE WINDOW SHOWS? — Yes, and that is why the render pane
//     is SpikeRenderPane in a file of its own: the window and the test compose the same function, so
//     the frame is the same frame rather than a second one that looks like it.
//
// (5) DOES A paginated_list BODY FAIL? — Yes: LocalKompotPageLoader is not provided by KompotPreview,
//     and the second sample below dies inside the render with "not provided". Recorded, not fixed —
//     the parameter is B-02's, and a spike that fixed what it found would stop being a measurement.

private const val TITLE = "kompot studio — B-08 spike"

// Jewel's own condition, asked with Jewel's own API rather than approximated. The obvious
// approximation — "java.vendor contains JetBrains" — is simply wrong: a JetBrains Runtime reports
// `java.vendor = Oracle Corporation`, like the OpenJDK it is built from, and the studio would take
// the undecorated path on the very runtime it provisions. Measured, not assumed: the first version of
// this file guessed, and the window it opened said so.
private val runningOnJetBrainsRuntime: Boolean = JBR.isAvailable()

internal fun sample(name: String): String =
    checkNotNull(object {}.javaClass.getResourceAsStream("/$name")) { "the sample $name is not in the resources" }
        .use { it.readBytes().decodeToString() }

public fun main() {
    application {
        var dark by remember { mutableStateOf(false) }
        val bodyState = rememberTextFieldState(sample("spike-screen.json"))

        val themeDefinition =
            if (dark) JewelTheme.darkThemeDefinition() else JewelTheme.lightThemeDefinition()

        IntUiTheme(theme = themeDefinition, styling = ComponentStyling.decoratedWindow()) {
            val windowState = rememberWindowState(width = 1280.dp, height = 860.dp)

            if (runningOnJetBrainsRuntime) {
                DecoratedWindow(onCloseRequest = ::exitApplication, title = TITLE, state = windowState) {
                    ReportWindow(window, decorated = true)
                    TitleBar(Modifier.newFullscreenControls()) { Text(TITLE) }
                    SpikeWindowContent(bodyState, dark) { dark = it }
                }
            } else {
                Window(onCloseRequest = ::exitApplication, title = TITLE, state = windowState) {
                    ReportWindow(window, decorated = false)
                    SpikeWindowContent(bodyState, dark) { dark = it }
                }
            }
        }
    }
}

// Question (1), printed rather than inferred. A window that fails to appear leaves a process that is
// perfectly alive and a log that says nothing, so "it ran for thirty seconds without an exception" is
// not an answer — this line is. It survives the spike only as long as the spike does.
//
// It WAITS for the window rather than reading it once: on the first composition the frame is not on
// screen yet and AWT's default 80x28 is what a naive probe prints — a number that looks like a
// finding and is only impatience.
@Composable
private fun ReportWindow(
    window: ComposeWindow,
    decorated: Boolean,
) {
    LaunchedEffect(window) {
        val shown =
            withTimeoutOrNull(SHOW_TIMEOUT_MS) {
                while (!window.isShowing) delay(POLL_MS)
                true
            } ?: false

        println(
            "kompot-studio spike: window showing=$shown size=${window.width}x${window.height} " +
                "decorated=$decorated jbr=${JBR.isAvailable()} " +
                "runtime=${System.getProperty("java.runtime.name")} " +
                "vendorVersion=${System.getProperty("java.vendor.version")}",
        )
    }
}

private const val SHOW_TIMEOUT_MS = 10_000L
private const val POLL_MS = 100L

@Composable
private fun SpikeWindowContent(
    bodyState: TextFieldState,
    dark: Boolean,
    onDarkChange: (Boolean) -> Unit,
) {
    val body = bodyState.text.toString()

    // Keyed on the body, so a fixed mistake stops being reported without anybody clearing anything.
    // The list is written to from inside composition, which is what a render-time degradation is:
    // the writes are deduplicated, so the invalidation they cause settles after one extra pass.
    val degradations = remember(body) { mutableStateListOf<String>() }
    val findings = remember(body) { diagnose(body) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CheckboxRow(text = "Dark", checked = dark, onCheckedChange = onDarkChange)
            DefaultButton(onClick = { bodyState.setTextAndPlaceCursorAtEnd(sample("spike-screen.json")) }) {
                Text("Screen")
            }
            // The body that is expected to fail, kept a click away rather than described in a
            // comment: question (5) is only answered by somebody watching it fail.
            OutlinedButton(onClick = { bodyState.setTextAndPlaceCursorAtEnd(sample("spike-paginated.json")) }) {
                Text("paginated_list (fails — B-02)")
            }
        }

        HorizontalSplitLayout(
            first = {
                TextArea(state = bodyState, modifier = Modifier.fillMaxSize().padding(8.dp))
            },
            second = {
                SpikeRenderPane(body = body, dark = dark, modifier = Modifier.fillMaxSize()) { kind, type ->
                    val line = "$kind: $type"
                    if (line !in degradations) degradations += line
                }
            },
            modifier = Modifier.fillMaxWidth().weight(1f),
            firstPaneMinWidth = 320.dp,
            secondPaneMinWidth = 320.dp,
        )

        Column(
            Modifier.fillMaxWidth().height(180.dp).padding(12.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val lines =
                findings.map { "${it.layer}: ${it.message}" } + degradations.map { "degradation: $it" }
            if (lines.isEmpty()) {
                Text("No findings.")
            } else {
                lines.forEach { Text(it) }
            }
        }
    }
}
