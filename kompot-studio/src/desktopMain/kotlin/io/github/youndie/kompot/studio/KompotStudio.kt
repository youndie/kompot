package io.github.youndie.kompot.studio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import io.github.youndie.kompot.studio.export.exportDsl
import io.github.youndie.kompot.studio.palette.PalettePane
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
import org.jetbrains.jewel.ui.component.HorizontalSplitLayout
import org.jetbrains.jewel.ui.component.OutlinedButton
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.SegmentedControl
import org.jetbrains.jewel.ui.component.SegmentedControlButtonData
import org.jetbrains.jewel.ui.component.SplitLayoutState
import androidx.compose.foundation.layout.size
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Tooltip
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

    // EXPORT, beside the save and going to the same place with a different extension. A draft is only
    // ever a draft, so it is written next to the body it came from rather than into a source tree the
    // studio has no business knowing about — where it goes from there is a person's decision.
    var exported by remember(body) { mutableStateOf("") }

    fun exportKotlin() {
        val target = saveTo?.let { it.resolveSibling(it.fileName.toString().substringBeforeLast('.') + ".kt") } ?: return
        val parsed = runCatching { Json.parseToJsonElement(body) }.getOrNull() ?: return
        target.parent?.let { Files.createDirectories(it) }
        Files.writeString(target, exportDsl(config, parsed, functionName = target.fileName.toString().removeSuffix(".kt")))
        // The name and not the path: the path is five wrapped lines in a row that has one, and the
        // directory is the one the body itself came from.
        exported = "drafted ${target.fileName}"
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

    // THE LAYOUT, and the rule it follows is the one every tool window somebody already knows
    // follows: a sidebar for what there is, a main area for the thing being worked on, a drawer for
    // what the tool has to say about it. Left: screens, structure, palette. Middle: the text and,
    // under it, the properties of the node the tree selected. Right: the screen as a client draws it.
    // Bottom: findings and actions. One thing stretches in each column — the tree, the editor, the
    // frame — and everything else is as tall as it needs to be, so no panel can eat another.
    val isForm = parsed is JsonObject && kompotBodyShape(parsed) == KompotBodyShape.FORM
    val sidebarSplit = remember { SplitLayoutState(SIDEBAR_SHARE) }
    val mainSplit = remember { SplitLayoutState(EDITOR_SHARE) }

    Column(Modifier.fillMaxSize()) {
        Toolbar(
            title = screen?.ref?.title ?: if (opened.isEmpty()) "sample screen" else "no screen selected",
            status = status,
            brands = config.brands,
            brand = brand,
            onBrand = onBrandChange,
            dark = dark,
            onDark = onDarkChange,
            device = device,
            onDevice = { device = it },
            formState = if (isForm) formState else null,
            onFormState = { formState = it },
            canSave = saveTo != null,
            onSave = ::save,
            onExport = ::exportKotlin,
            note = listOf(captureStatus, exported).filter { it.isNotEmpty() }.joinToString("  ·  "),
            capture =
                if (capture == null || goldenFile == null) {
                    null
                } else {
                    {
                        val safe = remember(config, body) { capturingIsSafe(config, body) }

                        OutlinedButton(onClick = {
                            // The frame this would write is drawn with a stubbed page loader, so it
                            // shows a list ending where it does not. Refusing outright would be wrong
                            // — somebody may want the picture anyway — but writing it silently is how
                            // a stub becomes a golden nobody remembers agreeing to.
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
                    }
                },
        )
        Divider(Orientation.Horizontal)

        HorizontalSplitLayout(
            first = {
                Sidebar(
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
                        PalettePane(
                            config = config,
                            modifier = Modifier.fillMaxWidth().heightIn(max = PALETTE_MAX_HEIGHT),
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
                            onMoveUp = { apply(JsonEdits.moveUp(body, selected!!.path)) },
                            onMoveDown = { apply(JsonEdits.moveDown(body, selected!!.path)) },
                            onDuplicate = { apply(JsonEdits.duplicate(body, selected!!.path)) },
                            onDelete = { apply(JsonEdits.delete(body, selected!!.path)) },
                        )
                    },
                ) { selected = it }
            },
            second = {
                Column(Modifier.fillMaxSize()) {
                    HorizontalSplitLayout(
                        first = {
                            Column(Modifier.fillMaxSize()) {
                                BodyEditor(
                                    state = bodyState,
                                    lexed = lexed,
                                    errorOffset = findings.firstOrNull { it.layer == "syntax" }?.offset,
                                    modifier = Modifier.fillMaxWidth().weight(1f).padding(8.dp),
                                )

                                // Under the text and not under the whole window: it is about one node
                                // of this body, and it takes its height from nothing that stretches.
                                val node = selected
                                if (node != null) {
                                    Divider(Orientation.Horizontal)
                                    SectionHeader("Properties", detail = node.wireType + (node.id?.let { " · $it" } ?: ""))
                                    InspectorPane(
                                        config = config,
                                        node = node,
                                        body = body,
                                        modifier = Modifier.fillMaxWidth().height(INSPECTOR_HEIGHT),
                                    ) { edited ->
                                        history.record(edited)
                                        bodyState.setTextAndPlaceCursorAtEnd(edited)
                                    }
                                }
                            }
                        },
                        second = {
                            // Beside the frame rather than instead of it: what a golden disagrees about
                            // is only readable next to what the screen actually draws.
                            Row(Modifier.fillMaxSize().padding(8.dp)) {
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
                                        // once per node per pass: without it the list grows for as long
                                        // as the window is open, and the writes never settle.
                                        if (degradations.none { it.message == finding.message }) {
                                            degradations += finding
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        firstPaneMinWidth = 320.dp,
                        secondPaneMinWidth = 320.dp,
                        state = mainSplit,
                    )

                    Divider(Orientation.Horizontal)
                    Drawer(
                        findings = findings + degradations,
                        actions = actions,
                        opened = opened,
                        onFinding = { finding ->
                            // Clicking a finding selects the node it is about — the two carry the same
                            // notation, so the join is an equality rather than a parse. A finding with
                            // no node (a syntax error, a degradation that names only a type) selects
                            // nothing rather than guessing.
                            selected = finding.path?.let { path -> tree?.flatten()?.firstOrNull { it.path == path } } ?: selected
                        },
                        onNavigate = { screen = it },
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().weight(1f),
            firstPaneMinWidth = 240.dp,
            secondPaneMinWidth = 640.dp,
            state = sidebarSplit,
        )
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
    // Nothing, rather than "no screen selected": the toolbar title already says so, and the same
    // words twice one above the other read as a stutter.
    if (selected == null) return ""

    val state by opened[selected.source].session.body(selected.ref).collectAsState()

    LaunchedEffect(selected, state.revisions) {
        state.text?.let { bodyState.setTextAndPlaceCursorAtEnd(it) }
    }

    val counts = "polled ${state.checks}, changed ${state.revisions}"
    return state.error?.let { "$counts — $it" } ?: counts
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

// A drop that has not happened yet because it would overwrite something.
private data class PendingDrop(
    val payload: String,
    val target: DropTarget,
    val targetLabel: String,
)

// An id nothing else in the body carries. Read out of the text rather than counted off the tree: a
// body can hold ids the tree does not reach — a form's fields, a type outside the profile — and an id
// that collides is a node the studio's own path notation can no longer tell apart.
private fun nextNodeId(
    body: String,
    wireType: String,
): String = generateSequence(1) { it + 1 }.first { !body.contains("\"${wireType}_$it\"") }.let { "${wireType}_$it" }


// ---- The chrome: toolbar, sidebar, drawer, and the pieces they share. ----

// Sizes on an 8-pixel grid, and shares rather than widths for the dividers: a share survives a
// window somebody resized, a width does not.
private const val SIDEBAR_SHARE = 0.24f
private const val EDITOR_SHARE = 0.52f
private val INSPECTOR_HEIGHT = 220.dp
private val SCREENS_MAX_HEIGHT = 200.dp
private val PALETTE_MAX_HEIGHT = 200.dp
private val DRAWER_MAX_HEIGHT = 160.dp
private val GUTTER = 12.dp
private val GAP = 8.dp

// What is being looked at on the left, what changes how it is drawn in the middle, what can be done
// with it on the right — with the one-line status beside the actions it reports on.
@Composable
private fun Toolbar(
    title: String,
    status: String,
    brands: List<String>,
    brand: String?,
    onBrand: (String?) -> Unit,
    dark: Boolean,
    onDark: (Boolean) -> Unit,
    device: DevicePreset,
    onDevice: (DevicePreset) -> Unit,
    formState: FormState?,
    onFormState: (FormState) -> Unit,
    canSave: Boolean,
    onSave: () -> Unit,
    onExport: () -> Unit,
    note: String,
    capture: (@Composable RowScope.() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = GUTTER, vertical = GAP),
        horizontalArrangement = Arrangement.spacedBy(GUTTER),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // The status line, and it is where the sources pay for themselves: "polled 42, changed 1"
            // is what a working ETag looks like, and "polled 42, changed 42" is a server that ignores
            // If-None-Match. Both draw the same screen, so nothing else in this window can tell them
            // apart.
            if (status.isNotEmpty()) Dim(status)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(GUTTER), verticalAlignment = Alignment.CenterVertically) {
            // Only when there is a choice to make. A deployment with one brand — or none — should not
            // be looking at a control that cannot change anything.
            if (brands.size > 1) Segmented(brands, brand, { it.orEmpty() }) { onBrand(it) }
            Segmented(listOf(false, true), dark, { if (it) "Dark" else "Light" }) { onDark(it) }
            Segmented(DEVICE_PRESETS, device, { it.label }) { onDevice(it) }
            // Only for a body that IS a form. On a screen the three states are one picture, and three
            // controls that all draw the same thing teach a reader to ignore them.
            if (formState != null) Segmented(FormState.entries, formState, { it.name.lowercase() }) { onFormState(it) }
        }

        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(GAP, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (note.isNotEmpty()) Dim(note, Modifier.weight(1f, fill = false))
            capture?.invoke(this)
            OutlinedButton(onClick = onSave, enabled = canSave) { Text("Save") }
            OutlinedButton(onClick = onExport, enabled = canSave) { Text("Kotlin") }
        }
    }
}

// One control for every "pick one of these" in the toolbar, so the brand, the theme, the device and
// the form state read as the same kind of choice — which they are.
@Composable
private fun <T> Segmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    SegmentedControl(
        options.map { option ->
            SegmentedControlButtonData(
                selected = option == selected,
                content = { Text(label(option)) },
                onSelect = { onSelect(option) },
            )
        },
    )
}

@Composable
private fun Dim(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(text, modifier, color = JewelTheme.globalColors.text.info, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

// A section is a header and a body, and the header is the same everywhere: a name, a detail beside it
// in the dim colour, a count on the right when there is one, and a chevron when it can close.
@Composable
private fun SectionHeader(
    title: String,
    detail: String? = null,
    trailing: String? = null,
    expanded: Boolean? = null,
    onToggle: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onToggle == null) Modifier else Modifier.clickable(onClick = onToggle))
            .padding(horizontal = GUTTER, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (expanded != null) Text(if (expanded) "▾" else "▸", Modifier.width(12.dp))
        Text(title)
        if (detail != null) Dim(detail, Modifier.weight(1f, fill = false))
        Spacer(Modifier.weight(1f))
        if (trailing != null) Dim(trailing)
    }
}

// The left column holds three lists, and they are three different questions: which BODY to look at,
// which NODE of it, and what could be ADDED. Stacked rather than tabbed, because picking a screen and
// then a node inside it is one move, and a tab would hide the first half the moment the second is
// used. The tree is the one that stretches; the other two close.
@Composable
private fun Sidebar(
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
    var screensOpen by remember { mutableStateOf(true) }
    var paletteOpen by remember { mutableStateOf(true) }

    Column(Modifier.fillMaxSize()) {
        if (opened.isNotEmpty() || stories.isNotEmpty() || fixtures.isNotEmpty()) {
            SectionHeader("Screens", expanded = screensOpen, onToggle = { screensOpen = !screensOpen })
            if (screensOpen) {
                ScreensPane(
                    opened = opened,
                    selected = screen,
                    stories = stories,
                    fixtures = fixtures,
                    onSelect = onScreen,
                    onStory = onStory,
                    onFixture = onFixture,
                    modifier = Modifier.fillMaxWidth().heightIn(max = SCREENS_MAX_HEIGHT),
                )
            }
            Divider(Orientation.Horizontal)
        }

        SectionHeader("Structure", trailing = tree?.flatten()?.size?.let { "$it nodes" })
        ScreenTreePane(tree, Modifier.fillMaxWidth().weight(1f), onDrop, onNode)
        pending()
        edits()

        Divider(Orientation.Horizontal)
        SectionHeader("Palette", expanded = paletteOpen, onToggle = { paletteOpen = !paletteOpen })
        if (paletteOpen) palette()
    }
}

// The four structural edits, next to the tree they act on. Buttons rather than a context menu: this
// is a tool for somebody who does not yet know what it can do. Disabled rather than hidden when
// nothing is selected — a row of controls that appears and disappears is a row nobody learns.
@Composable
private fun EditRow(
    enabled: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = GAP, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Glyphs with a tooltip rather than words: four worded buttons do not fit a sidebar, and a
        // sidebar that has to be widened before its buttons are legible is one nobody widens.
        EditButton("↑", "Move up", enabled, onMoveUp)
        EditButton("↓", "Move down", enabled, onMoveDown)
        EditButton("⧉", "Duplicate", enabled, onDuplicate)
        EditButton("✕", "Delete", enabled, onDelete)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EditButton(
    glyph: String,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Tooltip(tooltip = { Text(label) }) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(28.dp)) { Text(glyph) }
    }
}

@Composable
private fun ReplaceRow(
    label: String,
    onReplace: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        Modifier.padding(horizontal = GUTTER, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Replace $label?")
        OutlinedButton(onClick = onReplace) { Text("Replace") }
        OutlinedButton(onClick = onCancel) { Text("Cancel") }
    }
}

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
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 4.dp)) {
        opened.forEachIndexed { index, source ->
            val screens by source.session.screens.collectAsState()
            if (opened.size > 1) Dim(source.name, Modifier.padding(horizontal = GUTTER, vertical = 2.dp))
            screens.forEach { ref ->
                ListRow(
                    text = if (ref.kind == "screen") ref.title else "${ref.title} · ${ref.kind}",
                    selected = selected?.source == index && selected.ref == ref,
                ) { onSelect(SelectedScreen(index, ref)) }
            }
        }

        if (stories.isNotEmpty()) Dim("Stories", Modifier.padding(horizontal = GUTTER, vertical = 2.dp))
        stories.forEach { story ->
            // A story with no body is listed and does nothing: the gap is the message, and a row that
            // vanished would answer "which component has nobody drawn" with silence.
            ListRow(
                text = "${story.group} · ${story.name}" + if (story.body == null) "  (no sample)" else "",
                selected = false,
                onClick = if (story.body == null) null else ({ onStory(story) }),
            )
        }

        if (fixtures.isNotEmpty()) Dim("Screenshot fixtures", Modifier.padding(horizontal = GUTTER, vertical = 2.dp))
        fixtures.forEach { story ->
            ListRow(text = "${story.group}_${story.name}", selected = false) { onFixture(story) }
        }
    }
}

// One row for every list on the left, so a screen, a story and a fixture are picked the same way and
// look picked the same way.
@Composable
private fun ListRow(
    text: String,
    selected: Boolean,
    onClick: (() -> Unit)?,
) {
    Text(
        text,
        Modifier
            .fillMaxWidth()
            .then(if (selected) Modifier.background(JewelTheme.globalColors.outlines.focused.copy(alpha = 0.18f)) else Modifier)
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
            .padding(horizontal = GUTTER, vertical = 3.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private enum class DrawerTab { FINDINGS, ACTIONS }

// What the tool has to say, in one place at the bottom, with a count on the tab so a closed drawer
// still says whether there is anything in it. Findings first: an action is something that happened,
// a finding is something wrong.
@Composable
private fun Drawer(
    findings: List<Finding>,
    actions: List<LoggedAction>,
    opened: List<OpenSource>,
    onFinding: (Finding) -> Unit,
    onNavigate: (SelectedScreen) -> Unit,
) {
    var tab by remember { mutableStateOf(DrawerTab.FINDINGS) }
    var open by remember { mutableStateOf(true) }
    val errors = findings.count { it.severity == Severity.ERROR }
    val findingsLabel = if (findings.isEmpty()) "Findings" else "Findings · ${findings.size}" + if (errors > 0) " ($errors errors)" else ""
    val actionsLabel = if (actions.isEmpty()) "Actions" else "Actions · ${actions.size}"

    Row(
        Modifier.fillMaxWidth().padding(horizontal = GUTTER, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (open) "▾" else "▸", Modifier.width(12.dp).clickable { open = !open })
        Segmented(DrawerTab.entries, tab, { if (it == DrawerTab.FINDINGS) findingsLabel else actionsLabel }) {
            tab = it
            open = true
        }
    }

    if (!open) return

    Column(
        Modifier.fillMaxWidth().heightIn(min = 40.dp, max = DRAWER_MAX_HEIGHT).verticalScroll(rememberScrollState())
            .padding(horizontal = GUTTER, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when (tab) {
            DrawerTab.FINDINGS -> {
                if (findings.isEmpty()) Dim("No findings — the body is what this build's profile expects.")
                // Errors first, and only then the warnings: a degradation is the protocol working as
                // designed, and a page of them above the one line that says the body is malformed
                // buries it.
                findings.sortedBy { it.severity.ordinal }.forEach { finding ->
                    val marker = if (finding.severity == Severity.ERROR) "×" else "⚠"
                    val where = finding.path?.let { " $it" }.orEmpty()
                    Text(
                        text = "$marker ${finding.layer}$where — ${finding.message}",
                        modifier = Modifier.clickable { onFinding(finding) },
                    )
                }
            }

            DrawerTab.ACTIONS -> {
                if (actions.isEmpty()) Dim("Nothing tapped yet — actions the frame raises are listed here.")
                // The one line in the log the studio can act on: a navigate names a deeplink, and an
                // HTTP source has already read the graph that maps deeplinks to endpoints. Clicking it
                // opens that screen.
                actions.asReversed().forEach { logged ->
                    val target = logged.deeplink?.let { deeplink -> routeFor(opened, deeplink) }
                    Text(
                        text = if (target == null) logged.text else "${logged.text}  ↗",
                        modifier = if (target == null) Modifier else Modifier.clickable { onNavigate(target) },
                    )
                }
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
