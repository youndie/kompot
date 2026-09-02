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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
    // The body the window opens with. A stand-in until B-10 gives the studio real sources — a file, a
    // directory it watches, an endpoint with an ETag — at which point the window opens on a source
    // rather than on a string somebody passed.
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
    val body = bodyState.text.toString()

    // Keyed on the body, so a fixed mistake stops being reported without anybody clearing anything.
    // The list is written to from inside composition, which is what a render-time degradation is: the
    // writes are deduplicated, so the invalidation they cause settles after one extra pass.
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
        }

        HorizontalSplitLayout(
            first = {
                TextArea(state = bodyState, modifier = Modifier.fillMaxSize().padding(8.dp))
            },
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
            if (lines.isEmpty()) Text("No findings.") else lines.forEach { Text(it) }
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
