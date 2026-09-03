package io.github.youndie.kompot.studio

import androidx.compose.foundation.clickable
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
import io.github.youndie.kompot.KompotActionHandler
import io.github.youndie.kompot.preview.KompotBodyShape
import io.github.youndie.kompot.preview.kompotBodyShape
import io.github.youndie.kompot.spec.childSlots
import io.github.youndie.kompot.studio.diagnostics.Finding
import io.github.youndie.kompot.studio.diagnostics.Severity
import io.github.youndie.kompot.studio.diagnostics.capturingIsSafe
import io.github.youndie.kompot.studio.diagnostics.degradationFinding
import io.github.youndie.kompot.studio.diagnostics.diagnose
import io.github.youndie.kompot.studio.source.ScreenRef
import io.github.youndie.kompot.studio.source.ScreenSourceSession
import io.github.youndie.kompot.studio.source.open
import io.github.youndie.kompot.studio.capture.FrameCapture
import io.github.youndie.kompot.studio.capture.FrameDiff
import io.github.youndie.kompot.studio.capture.frameCaptureOrNull
import io.github.youndie.kompot.studio.edit.EditHistory
import io.github.youndie.kompot.studio.stories.Story
import io.github.youndie.kompot.studio.stories.ViddikStory
import io.github.youndie.kompot.studio.stories.storiesFor
import io.github.youndie.kompot.studio.stories.viddikStories
import io.github.youndie.kompot.studio.edit.JsonEdits
import io.github.youndie.kompot.studio.editor.BodyEditor
import io.github.youndie.kompot.studio.editor.lexJson
import io.github.youndie.kompot.studio.inspector.InspectorPane
import io.github.youndie.kompot.studio.tree.ScreenNode
import io.github.youndie.kompot.studio.palette.PaletteColumn
import io.github.youndie.kompot.studio.palette.newNode
import io.github.youndie.kompot.studio.tree.DropTarget
import io.github.youndie.kompot.studio.tree.Dragged
import io.github.youndie.kompot.studio.tree.ScreenTreePane
import io.github.youndie.kompot.studio.tree.canMove
import io.github.youndie.kompot.studio.tree.dragPayload
import io.github.youndie.kompot.studio.tree.dropTargetFor
import io.github.youndie.kompot.studio.tree.screenTree
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalTime
import javax.imageio.ImageIO
import java.time.format.DateTimeFormatter
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.HorizontalSplitLayout
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.RadioButtonRow
import org.jetbrains.jewel.ui.component.Text
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
            // A holder rather than a state: the handler is replaced on every composition, and making
            // that a state would recompose the window to install the handler the recomposition
            // produced.
            val shortcuts = remember { arrayOfNulls<(androidx.compose.ui.input.key.KeyEvent) -> Boolean>(1) }
            val onKey: (androidx.compose.ui.input.key.KeyEvent) -> Boolean = { shortcuts[0]?.invoke(it) == true }

            if (JBR.isAvailable()) {
                DecoratedWindow(
                    onCloseRequest = ::exitApplication,
                    title = title,
                    state = windowState,
                    onKeyEvent = onKey,
                ) {
                    ReportWindow(window, decorated = true)
                    TitleBar(Modifier.newFullscreenControls()) { Text(title) }
                    StudioWindowContent(config, bodyState, brand, dark, { brand = it }, { dark = it }) {
                        shortcuts[0] = it
                    }
                }
            } else {
                Window(
                    onCloseRequest = ::exitApplication,
                    title = title,
                    state = windowState,
                    onKeyEvent = onKey,
                ) {
                    ReportWindow(window, decorated = false)
                    StudioWindowContent(config, bodyState, brand, dark, { brand = it }, { dark = it }) {
                        shortcuts[0] = it
                    }
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
    // The window owns the key events and the content owns the state they act on, so the content hands
    // a handler back up rather than the window reaching down for a history it does not have.
    onShortcuts: ((androidx.compose.ui.input.key.KeyEvent) -> Boolean) -> Unit = {},
) {
    val opened = rememberOpenSources(config)
    var screen by remember(opened) { mutableStateOf<SelectedScreen?>(null) }

    // The status line, and it is where the sources pay for themselves: "polled 42, changed 1" is what
    // a working ETag looks like, and "polled 42, changed 42" is a server that ignores If-None-Match.
    // Both draw the same screen, so nothing else in this window can tell them apart.
    val status = SelectedBody(opened, screen, bodyState)

    val body = bodyState.text.toString()
    // Keyed on everything that can change what the render reports: a brand whose kit lacks a token
    // and a body whose node lacks a renderer degrade for different reasons, and neither should be
    // read off a stale list.
    val degradations = remember(body, brand, dark) { mutableStateListOf<Finding>() }
    val findings = remember(config, body) { diagnose(config, body) }

    var device by remember { mutableStateOf(DEVICE_PRESETS.first()) }
    var formState by remember { mutableStateOf(FormState.EMPTY) }
    val actions = remember(body) { mutableStateListOf<LoggedAction>() }

    val slots = remember(config) { childSlots(config.schemas) }
    // Null when the body does not parse, which is most keystrokes: the tree then keeps showing
    // nothing rather than flickering between half-typed shapes, and the syntax finding below says why.
    val parsed: JsonElement? = remember(body) { runCatching { Json.parseToJsonElement(body) }.getOrNull() }

    // What the frame is drawn from: the last text that PARSED, and only after the typing has stopped.
    // Two separate reasons, and both are about the half-typed state being the normal one. Re-rendering
    // per keystroke redraws a screen nobody has finished describing; rendering a body that does not
    // parse cannot be done at all, and blanking the frame while somebody deletes a comma would make
    // the studio unusable exactly where it is meant to help.
    var rendered by remember { mutableStateOf(body) }
    LaunchedEffect(body) {
        if (parsed == null) return@LaunchedEffect
        delay(RENDER_DEBOUNCE_MS)
        rendered = body
    }

    val tree = remember(parsed, slots) { parsed?.let { screenTree(it, slots) } }
    var selected by remember(tree) { mutableStateOf<ScreenNode?>(null) }

    // One walk of the text answers both "what colour is this" and "where is that node", so the caret
    // cannot land somewhere the colours disagree with.
    val lexed = remember(body) { lexJson(body) }

    // Selecting a node — in the tree, or by clicking a finding — puts the caret on the word that names
    // it. The join is the path, which the tree, the findings and the lexer all print the same way.
    LaunchedEffect(selected, lexed) {
        val range = selected?.path?.let { lexed.nodes[it] } ?: return@LaunchedEffect
        bodyState.edit { selection = TextRange(range.first, (range.last + 1).coerceAtMost(length)) }
    }

    val previewState = remember(formState, parsed) { previewState(formState, parsed) }

    val history = remember(opened) { EditHistory(body) }

    val stories = remember(config) { storiesFor(config) }
    val fixtures = remember { viddikStories() }
    // A fixture is a COMPOSITION rather than a body, so looking at one is not "open this text": it
    // replaces the frame and leaves the editor alone. Null is the ordinary state — a body is showing.
    var fixture by remember { mutableStateOf<ViddikStory?>(null) }

    // Every structural edit is a whole new text, put through the same field somebody types into: the
    // text stays the single state, and the tree stays one of the ways to change it. Two states to keep
    // in step would already be one too many.
    fun apply(edited: String?) {
        if (edited == null) return
        history.record(edited)
        bodyState.setTextAndPlaceCursorAtEnd(edited)
    }

    // A drop into a slot with room for one OVERWRITES, and that is the single edit here somebody
    // cannot see coming from the gesture. It waits for a yes. Reset with the body, for the same reason
    // the capture confirmation is: a yes is about one drop and must not carry to the next.
    var pendingDrop by remember(body) { mutableStateOf<PendingDrop?>(null) }

    fun performDrop(
        payload: String,
        target: DropTarget,
    ) {
        val many = !target.replacing
        Dragged.path(payload)?.let { from ->
            // Refused silently rather than reported: dragging a container into itself is a slip of the
            // hand, and the node staying where it was says so more clearly than a message would.
            if (canMove(from, target)) {
                apply(JsonEdits.moveInto(body, from, target.parentPath, target.slot, target.index, many))
            }
            return
        }
        val wireType = Dragged.type(payload) ?: return
        val node = newNode(config, wireType, nextNodeId(body, wireType))
        apply(JsonEdits.insertInto(body, target.parentPath, target.slot, target.index, node, many))
    }

    fun drop(
        payload: String,
        targetPath: String,
    ) {
        val node = tree?.flatten()?.firstOrNull { it.path == targetPath } ?: return
        val target = dropTargetFor(node, slots) ?: return
        if (target.replacing) pendingDrop = PendingDrop(payload, target, node.label) else performDrop(payload, target)
    }

    // The palette's other gesture. A drag is the one the item is about, but a list of types that can
    // only be dragged is unusable with a trackpad and untestable by anybody without a mouse; the click
    // ends in exactly the same edit.
    fun add(wireType: String) {
        val node = selected ?: return
        drop(Dragged.NEW + wireType, node.path)
    }

    fun undoOrRedo(text: String?) {
        if (text != null) bodyState.setTextAndPlaceCursorAtEnd(text)
    }

    // Asked once. Everything that can be wrong with the reflective binding is wrong at construction,
    // so the window either has these buttons for its whole life or never shows them.
    val capture = remember { frameCaptureOrNull() }
    var comparison by remember(body, brand, dark) { mutableStateOf<FrameDiff?>(null) }
    var captureStatus by remember(body, brand, dark) { mutableStateOf("") }
    // Reset with the body: a confirmation is about THIS screen, and carrying one across an edit would
    // make the second capture the unguarded one.
    var captureConfirmed by remember(body) { mutableStateOf(false) }

    val goldenFile: Path? =
        config.snapshotsDirectory?.resolve(
            config.goldenName(brand, dark, screen?.ref?.title ?: "screen"),
        )

    fun snap(): BufferedImage? {
        val engine = capture ?: return null
        val width = device.width ?: GOLDEN_WIDTH
        val height = device.height ?: GOLDEN_HEIGHT

        // The SAME composition the window draws, taken through the same function — a picture assembled
        // beside it would be a picture of the copy. No viddik composition local is provided: the frame
        // already takes `dark` as a parameter, and a second, hidden channel for the same fact is how
        // the two come to disagree.
        return engine.capture(width, height, emptyList()) {
            StudioRenderPane(
                config = config,
                body = rendered,
                brand = brand,
                dark = dark,
                state = previewState,
                device = device,
            ) { _, _ -> }
        }
    }

    val saveTo: Path? = saveTarget(config, opened, screen)

    fun save() {
        val target = saveTo ?: return
        target.parent?.let { Files.createDirectories(it) }
        Files.writeString(target, body)
    }

    // Meta and not Ctrl: this window only opens on a desktop JVM, and every one of those on this
    // toolkit's machines is a Mac. A second binding is a B-20 question, once there is a gradle task
    // somebody runs on Linux.
    onShortcuts { event ->
        if (event.type != KeyEventType.KeyDown || !event.isMetaPressed) {
            false
        } else {
            when (event.key) {
                Key.S -> { save(); true }
                Key.Z -> { undoOrRedo(if (event.isShiftPressed) history.redo() else history.undo()); true }
                else -> false
            }
        }
    }

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

            DEVICE_PRESETS.forEach { preset ->
                RadioButtonRow(text = preset.label, selected = device == preset, onClick = { device = preset })
            }

            // Only for a body that IS a form. On a screen the three states are one picture, and three
            // controls that all draw the same thing teach a reader to ignore them.
            if (parsed is JsonObject && kompotBodyShape(parsed) == KompotBodyShape.FORM) {
                FormState.entries.forEach { candidate ->
                    RadioButtonRow(
                        text = candidate.name.lowercase(),
                        selected = formState == candidate,
                        onClick = { formState = candidate },
                    )
                }
            }

            if (capture != null && goldenFile != null) {
                val safe = remember(config, body) { capturingIsSafe(config, body) }

                OutlinedButton(onClick = {
                    // The frame this would write is drawn with a stubbed page loader, so it shows a
                    // list ending where it does not. Refusing outright would be wrong — somebody may
                    // want the picture anyway — but writing it silently is how a stub becomes a
                    // golden nobody remembers agreeing to.
                    if (!safe && !captureConfirmed) {
                        captureConfirmed = true
                        captureStatus =
                            "this frame is drawn with a stubbed page loader — press again to write it anyway"
                        return@OutlinedButton
                    }

                    val image = snap()
                    if (image == null) {
                        captureStatus = "nothing to capture"
                    } else {
                        Files.createDirectories(goldenFile.parent)
                        ImageIO.write(image, "png", goldenFile.toFile())
                        comparison = null
                        captureStatus = "wrote ${goldenFile.fileName}"
                    }
                }) { Text(if (safe || captureConfirmed) "Capture" else "Capture…") }

                OutlinedButton(onClick = {
                    val actual = snap()
                    val expected = goldenFile.takeIf { Files.isRegularFile(it) }?.let { ImageIO.read(it.toFile()) }
                    when {
                        actual == null -> captureStatus = "nothing to capture"
                        expected == null -> captureStatus = "no golden at ${goldenFile.fileName}"
                        else -> {
                            val diff = capture.diff(expected, actual)
                            comparison = diff
                            captureStatus = "${goldenFile.fileName}: ${"%.2f".format(diff.mismatchPercent)}% differ"
                        }
                    }
                }) { Text("Compare") }

                if (captureStatus.isNotEmpty()) Text(captureStatus)
            }

            if (status.isNotEmpty()) Text(status)
        }

        HorizontalSplitLayout(
            first = {
                ScreensAndTree(
                    opened = opened,
                    screen = screen,
                    tree = tree,
                    stories = stories,
                    fixtures = fixtures,
                    onScreen = { screen = it },
                    onStory = { story ->
                        fixture = null
                        story.body?.let { text ->
                            history.record(text)
                            bodyState.setTextAndPlaceCursorAtEnd(text)
                        }
                    },
                    onFixture = { fixture = it },
                    onDrop = ::drop,
                    palette = {
                        PaletteColumn(
                            config = config,
                            modifier = Modifier.fillMaxWidth().height(PALETTE_HEIGHT),
                            onAdd = ::add,
                            dragModifier = { wireType -> Modifier.dragPayload(Dragged.NEW + wireType) },
                        )
                    },
                    pending = {
                        val drop = pendingDrop
                        if (drop != null) {
                            ReplaceRow(
                                label = drop.targetLabel,
                                onReplace = {
                                    performDrop(drop.payload, drop.target)
                                    pendingDrop = null
                                },
                                onCancel = { pendingDrop = null },
                            )
                        }
                    },
                    edits = {
                        EditRow(
                            enabled = selected != null,
                            canSave = saveTo != null,
                            onMoveUp = { apply(JsonEdits.moveUp(body, selected!!.path)) },
                            onMoveDown = { apply(JsonEdits.moveDown(body, selected!!.path)) },
                            onDuplicate = { apply(JsonEdits.duplicate(body, selected!!.path)) },
                            onDelete = { apply(JsonEdits.delete(body, selected!!.path)) },
                            onSave = { save() },
                        )
                    },
                ) { selected = it }
            },
            second = {
                HorizontalSplitLayout(
                    first = {
                        BodyEditor(
                            state = bodyState,
                            lexed = lexed,
                            errorOffset = findings.firstOrNull { it.layer == "syntax" }?.offset,
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                        )
                    },
                    second = {
                        // Beside the frame rather than instead of it: what a golden disagrees about is
                        // only readable next to what the screen actually draws.
                        Row(Modifier.fillMaxSize()) {
                            comparison?.let { diff ->
                                Image(
                                    bitmap = diff.image.toComposeImageBitmap(),
                                    contentDescription = "the pixels a golden disagrees about",
                                    modifier = Modifier.weight(1f),
                                )
                            }

                            val picked = fixture
                            if (picked != null) {
                                // Inside the consumer's frame like anything else: a fixture drawn
                                // outside the brand would be a picture of a composition nobody ships.
                                DeviceFrame(device, Modifier.weight(1f).fillMaxSize()) {
                                    config.frame(brand, dark) { picked.content() }
                                }
                            } else {
                                StudioRenderPane(
                                    config = config,
                                    body = rendered,
                                    brand = brand,
                                    dark = dark,
                                    modifier = Modifier.weight(1f).fillMaxSize(),
                                    selectedId = selected?.id,
                                    state = previewState,
                                    device = device,
                                    actionHandler =
                                        KompotActionHandler { action ->
                                            actions += LoggedAction(LocalTime.now().format(CLOCK), action)
                                        },
                                ) { kind, type ->
                                    val finding = degradationFinding(kind, type)
                                    // Deduplicated because this is called from inside composition,
                                    // once per node per pass: without it the list grows for as long as
                                    // the window is open, and the writes never settle.
                                    if (degradations.none { it.message == finding.message }) {
                                        degradations += finding
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    firstPaneMinWidth = 280.dp,
                    secondPaneMinWidth = 280.dp,
                )
            },
            modifier = Modifier.fillMaxWidth().weight(1f),
            firstPaneMinWidth = 200.dp,
            secondPaneMinWidth = 560.dp,
        )

        // The inspector under the tree's column rather than beside the frame: it is about the node the
        // tree selected, and the two being far apart is what makes a properties panel feel like a
        // second application.
        if (selected != null) {
            InspectorPane(
                config = config,
                node = selected,
                body = body,
                modifier = Modifier.fillMaxWidth().height(INSPECTOR_HEIGHT),
            ) { edited ->
                history.record(edited)
                bodyState.setTextAndPlaceCursorAtEnd(edited)
            }
        }

        if (actions.isNotEmpty()) {
            ActionLogPane(actions, opened, Modifier.fillMaxWidth().height(ACTION_LOG_HEIGHT)) { target ->
                screen = target
            }
        }

        DiagnosticsPane(findings + degradations, Modifier.fillMaxWidth().height(180.dp)) { finding ->
            // Clicking a finding selects the node it is about — the two carry the same notation, so
            // the join is an equality rather than a parse. A finding with no node (a syntax error, a
            // degradation that names only a type) selects nothing rather than guessing.
            selected = finding.path?.let { path -> tree?.flatten()?.firstOrNull { it.path == path } } ?: selected
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

// The log, and the one line in it the studio can act on: a navigate names a deeplink, and an HTTP
// source has already read the graph that maps deeplinks to endpoints. Clicking it opens that screen.
@Composable
private fun ActionLogPane(
    actions: List<LoggedAction>,
    opened: List<OpenSource>,
    modifier: Modifier = Modifier,
    onNavigate: (SelectedScreen) -> Unit,
) {
    Column(
        modifier.padding(horizontal = 12.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        actions.asReversed().forEach { logged ->
            val target = logged.deeplink?.let { deeplink -> routeFor(opened, deeplink) }
            Text(
                text = if (target == null) logged.text else "${logged.text}  ↗",
                modifier = if (target == null) Modifier else Modifier.clickable { onNavigate(target) },
            )
        }
    }
}

private fun routeFor(
    opened: List<OpenSource>,
    deeplink: String,
): SelectedScreen? {
    opened.forEachIndexed { index, source ->
        val ref = source.session.screens.value.firstOrNull { it.deeplink == deeplink }
        if (ref != null) return SelectedScreen(index, ref)
    }
    return null
}

private val ACTION_LOG_HEIGHT = 120.dp
private val INSPECTOR_HEIGHT = 200.dp

// The canvas size design work is done at, used when nothing narrower was chosen: a golden has to have
// SOME size, and taking the pane's would make the picture depend on how wide somebody dragged a
// divider.
private const val GOLDEN_WIDTH = 393
private const val GOLDEN_HEIGHT = 852

// Long enough that a burst of keystrokes is one redraw and short enough that a pause reads as
// immediate. The frame is a whole composition of somebody else's renderers — this is not a text field
// repainting itself.
private const val RENDER_DEBOUNCE_MS = 150L

// Wall-clock and not a monotonic counter: two taps a second apart and two taps in the same frame look
// different in a log, and which of the two happened is the question somebody is asking.
private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

@Composable
private fun DiagnosticsPane(
    findings: List<Finding>,
    modifier: Modifier = Modifier,
    onSelect: (Finding) -> Unit,
) {
    Column(
        modifier.padding(12.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (findings.isEmpty()) {
            Text("No findings.")
            return@Column
        }

        // Errors first, and only then the warnings: a degradation is the protocol working as designed,
        // and a page of them above the one line that says the body is malformed buries it.
        findings.sortedBy { it.severity.ordinal }.forEach { finding ->
            val marker = if (finding.severity == Severity.ERROR) "×" else "⚠"
            val where = finding.path?.let { " $it" }.orEmpty()
            Text(
                text = "$marker ${finding.layer}$where — ${finding.message}",
                modifier = Modifier.clickable { onSelect(finding) },
            )
        }
    }
}

// The four structural edits and the save, next to the tree they act on. Buttons rather than a context
// menu: this is a tool for somebody who does not yet know what it can do.
@Composable
private fun EditRow(
    enabled: Boolean,
    canSave: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Disabled rather than hidden when nothing is selected: a row of controls that appears and
        // disappears is a row nobody learns.
        OutlinedButton(onClick = onMoveUp, enabled = enabled) { Text("↑") }
        OutlinedButton(onClick = onMoveDown, enabled = enabled) { Text("↓") }
        OutlinedButton(onClick = onDuplicate, enabled = enabled) { Text("Copy") }
        OutlinedButton(onClick = onDelete, enabled = enabled) { Text("Delete") }
        OutlinedButton(onClick = onSave, enabled = canSave) { Text("Save") }
    }
}

// Where a save goes, and it is the source that decides. A file and a directory are edited in place —
// the loop this exists for is "a test rewrote the fixture, fix it, save it back". An HTTP body has no
// file of its own, so it becomes a RECORDING, which is the step a deployment does by hand today.
private fun saveTarget(
    config: KompotStudioConfig,
    opened: List<OpenSource>,
    screen: SelectedScreen?,
): Path? {
    val selected = screen ?: return null
    val source = opened.getOrNull(selected.source) ?: return null

    return if (source.session.screens.value.any { it.deeplink != null }) {
        config.recordingsDirectory?.resolve(selected.ref.title.trim('/').ifEmpty { "screen" } + ".json")
    } else {
        Path.of(selected.ref.id)
    }
}

// The left column holds both lists, and they are different questions: which BODY to look at, and
// which NODE of it. Stacked rather than tabbed, because picking a screen and then a node inside it is
// one move, and a tab would hide the first half the moment the second is used.
@Composable
private fun ScreensAndTree(
    opened: List<OpenSource>,
    screen: SelectedScreen?,
    tree: ScreenNode?,
    stories: List<Story>,
    fixtures: List<ViddikStory>,
    onScreen: (SelectedScreen) -> Unit,
    onStory: (Story) -> Unit,
    onFixture: (ViddikStory) -> Unit,
    onDrop: (payload: String, targetPath: String) -> Unit,
    palette: @Composable () -> Unit,
    pending: @Composable () -> Unit,
    edits: @Composable () -> Unit,
    onNode: (ScreenNode) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (opened.isNotEmpty() || stories.isNotEmpty() || fixtures.isNotEmpty()) {
            ScreensPane(
                opened = opened,
                selected = screen,
                stories = stories,
                fixtures = fixtures,
                onSelect = onScreen,
                onStory = onStory,
                onFixture = onFixture,
                modifier = Modifier.fillMaxWidth().height(SOURCES_HEIGHT),
            )
        }
        ScreenTreePane(tree, Modifier.fillMaxWidth().weight(1f), onDrop, onNode)
        palette()
        pending()
        edits()
    }
}

private val SOURCES_HEIGHT = 200.dp
private val PALETTE_HEIGHT = 180.dp

// A drop that has not happened yet because it would overwrite something.
private data class PendingDrop(
    val payload: String,
    val target: DropTarget,
    val targetLabel: String,
)

@Composable
private fun ReplaceRow(
    label: String,
    onReplace: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Replace $label?")
        OutlinedButton(onClick = onReplace) { Text("Replace") }
        OutlinedButton(onClick = onCancel) { Text("Cancel") }
    }
}

// An id nothing else in the body carries. Read out of the text rather than counted off the tree: a
// body can hold ids the tree does not reach — a form's fields, a type outside the profile — and an id
// that collides is a node the studio's own path notation can no longer tell apart.
private fun nextNodeId(
    body: String,
    wireType: String,
): String = generateSequence(1) { it + 1 }.first { !body.contains("\"${wireType}_$it\"") }.let { "${wireType}_$it" }

@Composable
private fun ScreensPane(
    opened: List<OpenSource>,
    selected: SelectedScreen?,
    stories: List<Story>,
    fixtures: List<ViddikStory>,
    onSelect: (SelectedScreen) -> Unit,
    onStory: (Story) -> Unit,
    onFixture: (ViddikStory) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.padding(8.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
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

        if (stories.isNotEmpty()) Text("Stories")
        stories.forEach { story ->
            // A story with no body is listed and does nothing: the gap is the message, and a row that
            // vanished would answer "which component has nobody drawn" with silence.
            val label = "${story.group} · ${story.name}" + if (story.body == null) "  (no sample)" else ""
            Text(
                text = label,
                modifier = if (story.body == null) Modifier else Modifier.clickable { onStory(story) },
            )
        }

        if (fixtures.isNotEmpty()) Text("Screenshot fixtures")
        fixtures.forEach { story ->
            Text(
                text = "${story.group}_${story.name}",
                modifier = Modifier.clickable { onFixture(story) },
            )
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
