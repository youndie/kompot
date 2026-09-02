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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
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
import io.github.youndie.kompot.studio.source.ScreenRef
import io.github.youndie.kompot.studio.source.ScreenSourceSession
import io.github.youndie.kompot.studio.source.open
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.HorizontalSplitLayout
import org.jetbrains.jewel.ui.component.RadioButtonRow
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextArea
import org.jetbrains.jewel.window.DecoratedWindow
import org.jetbrains.jewel.window.TitleBar
import org.jetbrains.jewel.window.newFullscreenControls

// THE ENTRY POINT. A consumer writes a `main` of a dozen lines, and everything the studio draws with
// comes from the configuration it is handed — see KompotStudioConfig for why a brand cannot be a
// parameter of colours.
//
// Blocking, like every Compose Desktop `application`: it returns when the last window closes.
public fun kompotStudio(
    config: KompotStudioConfig,
    // The body the window opens with, before anything is selected. A configuration with sources
    // replaces it the moment somebody picks a screen; one without sources — the toolkit's own demo —
    // never does, and this is all it ever shows.
    body: String = SAMPLE_BODY,
    title: String = "kompot studio",
) {
    application {
        var dark by remember { mutableStateOf(false) }
        var brand by remember { mutableStateOf(config.brands.firstOrNull()) }
        val bodyState = rememberTextFieldState(body)

        val themeDefinition =
            if (dark) JewelTheme.darkThemeDefinition() else JewelTheme.lightThemeDefinition()

        IntUiTheme(theme = themeDefinition, styling = ComponentStyling.decoratedWindow()) {
            val windowState = rememberWindowState(width = 1280.dp, height = 860.dp)

            // The branch, and it is not defensiveness: DecoratedWindow's first statement is
            // `if (!JBR.isAvailable()) error(...)`. It does not degrade to a plain window — a studio
            // launched on any other JVM would die at startup with a message about Jewel rather than
            // about itself. `:kompot-studio:run` provisions a JetBrains Runtime, so this branch is for
            // the consumer who launches the jar with their own java.
            //
            // The condition is Jewel's own call and not a guess at `java.vendor`, which on a
            // JetBrains Runtime reads "Oracle Corporation" — the guess sent the studio down this path
            // on the very runtime the other one exists for.
            if (JBR.isAvailable()) {
                DecoratedWindow(onCloseRequest = ::exitApplication, title = title, state = windowState) {
                    ReportWindow(window, decorated = true)
                    TitleBar(Modifier.newFullscreenControls()) { Text(title) }
                    StudioWindowContent(config, bodyState, brand, dark, { brand = it }) { dark = it }
                }
            } else {
                Window(onCloseRequest = ::exitApplication, title = title, state = windowState) {
                    ReportWindow(window, decorated = false)
                    StudioWindowContent(config, bodyState, brand, dark, { brand = it }) { dark = it }
                }
            }
        }
    }
}

@Composable
private fun StudioWindowContent(
    config: KompotStudioConfig,
    bodyState: TextFieldState,
    brand: String?,
    dark: Boolean,
    onBrandChange: (String?) -> Unit,
    onDarkChange: (Boolean) -> Unit,
) {
    val opened = rememberOpenSources(config)
    var selected by remember(opened) { mutableStateOf<SelectedScreen?>(null) }

    // The status line, and it is where the sources pay for themselves: "polled 42, changed 1" is what
    // a working ETag looks like, and "polled 42, changed 42" is a server that ignores If-None-Match.
    // Both draw the same screen, so nothing else in this window can tell them apart.
    val status = SelectedBody(opened, selected, bodyState)

    val body = bodyState.text.toString()
    val degradations = remember(body, brand, dark) { mutableStateListOf<String>() }
    val findings = remember(config, body) { diagnose(config, body) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CheckboxRow(text = "Dark", checked = dark, onCheckedChange = onDarkChange)

            // Only when there is a choice to make. A deployment with one brand — or none — should not
            // be looking at a control that cannot change anything.
            if (config.brands.size > 1) {
                config.brands.forEach { name ->
                    RadioButtonRow(text = name, selected = brand == name, onClick = { onBrandChange(name) })
                }
            }

            if (status.isNotEmpty()) Text(status)
        }

        HorizontalSplitLayout(
            first = {
                ScreensPane(opened, selected) { selected = it }
            },
            second = {
                HorizontalSplitLayout(
                    first = { TextArea(state = bodyState, modifier = Modifier.fillMaxSize().padding(8.dp)) },
                    second = {
                        StudioRenderPane(
                            config = config,
                            body = body,
                            brand = brand,
                            dark = dark,
                            modifier = Modifier.fillMaxSize(),
                        ) { kind, type ->
                            val line = "$kind: $type"
                            if (line !in degradations) degradations += line
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    firstPaneMinWidth = 280.dp,
                    secondPaneMinWidth = 280.dp,
                )
            },
            modifier = Modifier.fillMaxWidth().weight(1f),
            firstPaneMinWidth = 180.dp,
            secondPaneMinWidth = 560.dp,
        )

        Column(
            Modifier.fillMaxWidth().height(180.dp).padding(12.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val lines =
                findings.map { "${it.layer}: ${it.message}" } + degradations.map { "degradation: $it" }
            if (lines.isEmpty()) Text("No findings.") else lines.forEach { Text(it) }
        }
    }
}

// One screen of one source, which is what a selection has to be: two sources can offer the same
// endpoint, and a ref alone would not say which session to ask.
private data class SelectedScreen(val source: Int, val ref: ScreenRef)

private class OpenSource(val name: String, val session: ScreenSourceSession)

// Opened once per configuration and closed with the window. The scope is the composition's, so a
// window that goes away takes its polling with it rather than leaving a thread reading a file nobody
// is looking at.
@Composable
private fun rememberOpenSources(config: KompotStudioConfig): List<OpenSource> {
    val scope = rememberCoroutineScope()
    val opened = remember(config, scope) { config.sources.map { OpenSource(it.name, it.open(scope)) } }
    DisposableEffect(opened) { onDispose { opened.forEach { it.session.close() } } }
    return opened
}

// Reads the selected body and pushes it into the editor when — and only when — it has actually
// changed. Keyed on the revision counter rather than on the text: keying on the text would overwrite
// whatever somebody is typing on every poll, and keying on nothing at all would never pick up the
// rewrite that is the whole point of watching a file.
@Composable
private fun SelectedBody(
    opened: List<OpenSource>,
    selected: SelectedScreen?,
    bodyState: TextFieldState,
): String {
    if (selected == null) return if (opened.isEmpty()) "" else "no screen selected"

    val state by opened[selected.source].session.body(selected.ref).collectAsState()

    LaunchedEffect(selected, state.revisions) {
        state.text?.let { bodyState.setTextAndPlaceCursorAtEnd(it) }
    }

    val counts = "polled ${state.checks}, changed ${state.revisions}"
    return state.error?.let { "$counts — $it" } ?: counts
}

@Composable
private fun ScreensPane(
    opened: List<OpenSource>,
    selected: SelectedScreen?,
    onSelect: (SelectedScreen) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(8.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (opened.isEmpty()) {
            Text("No sources configured.")
            return@Column
        }

        opened.forEachIndexed { index, source ->
            val screens by source.session.screens.collectAsState()
            Text(source.name)
            screens.forEach { ref ->
                RadioButtonRow(
                    text = if (ref.kind == "screen") ref.title else "${ref.title} · ${ref.kind}",
                    selected = selected?.source == index && selected.ref == ref,
                    onClick = { onSelect(SelectedScreen(index, ref)) },
                )
            }
        }
    }
}

// What the window turned out to be, printed rather than inferred. A window that fails to appear leaves
// a process that is perfectly alive and a log that says nothing, so "it ran for thirty seconds without
// an exception" is not an answer — this line is.
//
// It WAITS for the window rather than reading it once: on the first composition the frame is not on
// screen yet and AWT's default 80x28 is what a naive probe prints — a number that looks like a finding
// and is only impatience.
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
            "kompot studio: window showing=$shown size=${window.width}x${window.height} " +
                "decorated=$decorated jbr=${JBR.isAvailable()} " +
                "runtime=${System.getProperty("java.runtime.name")} " +
                "vendorVersion=${System.getProperty("java.vendor.version")}",
        )
    }
}

private const val SHOW_TIMEOUT_MS = 10_000L
private const val POLL_MS = 100L
