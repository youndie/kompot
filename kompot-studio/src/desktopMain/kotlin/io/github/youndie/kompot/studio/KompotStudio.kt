package io.github.youndie.kompot.studio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import io.github.youndie.kompot.studio.ui.installMagnification
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import kotlin.math.roundToInt
import org.jetbrains.jewel.ui.component.TextField
import io.github.youndie.kompot.studio.tree.kindFor
import io.github.youndie.kompot.studio.tree.DropKind
import io.github.youndie.kompot.studio.ui.EmptyState
import io.github.youndie.kompot.studio.ui.ConfirmPopup
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.runtime.SideEffect
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
import io.github.youndie.kompot.preview.decodeKompotBody
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
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.SegmentedControl
import org.jetbrains.jewel.ui.component.SegmentedControlButtonData
import org.jetbrains.jewel.ui.component.SplitLayoutState
import androidx.compose.foundation.layout.size
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Tooltip
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.youndie.kompot.studio.palette.paletteFor
import io.github.youndie.kompot.studio.ui.HRule
import io.github.youndie.kompot.studio.ui.Icon
import io.github.youndie.kompot.studio.ui.VRule
import io.github.youndie.kompot.studio.ui.SmallSegmented
import io.github.youndie.kompot.studio.ui.StudioIcon
import io.github.youndie.kompot.studio.ui.studioColors
import org.jetbrains.jewel.ui.component.DefaultButton
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
    // Compose's desktop accessibility is switched OFF unless somebody asked for it, and the reason
    // is a crash rather than taste. With an assistive client attached — a screen reader, a window
    // manager, an automation tool — Compose 1.11 answers the removal of a focused node by moving
    // the accessibility focus to an arbitrary node at the bottom of the tree, and the removal of
    // THAT node, whenever it comes, takes the window down inside the accessibility sync (an NPE in
    // ComposeSceneAccessibility.defaultAccessibilityFocusTarget). An editor removes nodes all day:
    // closing a popup, selecting another node, filtering a list. Reproduced three times, by three
    // routes, before this line. `-Dcompose.accessibility.enable=true` turns it back on.
    if (System.getProperty(ACCESSIBILITY_PROPERTY) == null) System.setProperty(ACCESSIBILITY_PROPERTY, "false")

    application {
        // Two darks, and they are different questions. The PREVIEW's is the screen being edited —
        // a switch in the toolbar, because the point is to look at both. The STUDIO's is the
        // operating system's, and nothing in the window changes it: a tool that goes dark because the
        // screen inside it did is a tool whose text vanishes the moment somebody checks a night mode.
        var dark by remember { mutableStateOf(false) }
        var brand by remember { mutableStateOf(config.brands.firstOrNull()) }
        val bodyState = rememberTextFieldState(body)

        val themeDefinition =
            if (isSystemInDarkTheme()) JewelTheme.darkThemeDefinition() else JewelTheme.lightThemeDefinition()

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
            // The same shape for the trackpad pinch: the window's root hears it, the preview acts on it.
            val magnify = remember { arrayOfNulls<(Double) -> Unit>(1) }

            if (JBR.isAvailable()) {
                DecoratedWindow(
                    onCloseRequest = ::exitApplication,
                    title = title,
                    state = windowState,
                    onKeyEvent = onKey,
                ) {
                    ReportWindow(window, decorated = true)
                    LaunchedEffect(window) { installMagnification(window.rootPane) { magnify[0]?.invoke(it) } }
                    TitleBar(Modifier.newFullscreenControls()) { Text(title) }
                    StudioWindowContent(config, bodyState, brand, dark, { brand = it }, { dark = it }, { magnify[0] = it }) {
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
                    LaunchedEffect(window) { installMagnification(window.rootPane) { magnify[0]?.invoke(it) } }
                    StudioWindowContent(config, bodyState, brand, dark, { brand = it }, { dark = it }, { magnify[0] = it }) {
                        shortcuts[0] = it
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun StudioWindowContent(
    config: KompotStudioConfig,
    bodyState: TextFieldState,
    brand: String?,
    dark: Boolean,
    onBrandChange: (String?) -> Unit,
    onDarkChange: (Boolean) -> Unit,
    // The pinch, the same way: the window hears it, the content says what it zooms.
    onMagnify: ((Double) -> Unit) -> Unit = {},
    // The window owns the key events and the content owns the state they act on, so the content hands
    // a handler back up rather than the window reaching down for a history it does not have.
    onShortcuts: ((androidx.compose.ui.input.key.KeyEvent) -> Boolean) -> Unit = {},
) {
    val opened = rememberOpenSources(config)
    var screen by remember(opened) { mutableStateOf<SelectedScreen?>(null) }

    // The status line, and it is where the sources pay for themselves: "polled 42, changed 1" is what
    // a working ETag looks like, and "polled 42, changed 42" is a server that ignores If-None-Match.
    // Both draw the same screen, so nothing else in this window can tell them apart.
    var savedBody by remember(opened) { mutableStateOf(bodyState.text.toString()) }
    val status = SelectedBody(opened, screen, bodyState) { savedBody = it }

    val body = bodyState.text.toString()
    // Keyed on everything that can change what the render reports: a brand whose kit lacks a token
    // and a body whose node lacks a renderer degrade for different reasons, and neither should be
    // read off a stale list.
    val degradations = remember(body, brand, dark) { mutableStateListOf<Finding>() }
    // Set by the render gate below, read here: a body the client cannot decode is an error of the
    // render layer, whatever the schema thinks of it.
    var undecodable by remember { mutableStateOf<String?>(null) }
    val findings =
        remember(config, body, undecodable) {
            diagnose(config, body) +
                listOfNotNull(
                    undecodable?.let {
                        Finding("render", null, "the client cannot decode this body — ${it.lineSequence().first()}", Severity.ERROR)
                    },
                )
        }
    // The worst finding per node, for the tree's margin.
    val marks =
        remember(findings, degradations.size) {
            (findings + degradations)
                .filter { it.path != null }
                .groupBy { it.path!! }
                .mapValues { (_, own) -> if (own.any { it.severity == Severity.ERROR }) Severity.ERROR else Severity.WARNING }
        }
    val paletteCount = remember(config) { paletteFor(config).size }

    var device by remember { mutableStateOf(DEVICE_PRESETS.first()) }
    // Null is "fit": the frame scales to the pane, and the number under the frame says what that
    // came to. Reset with the device, because a zoom chosen for a phone means nothing on a tablet.
    var zoom by remember(device) { mutableStateOf<Float?>(null) }
    var shownScale by remember { mutableStateOf(1f) }
    // Whether the pointer is over the preview: a pinch anywhere else in the window is not about it.
    var previewHovered by remember { mutableStateOf(false) }

    fun zoomBy(factor: Float) {
        zoom = ((zoom ?: shownScale) * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
    }
    // Registered on every composition, like the shortcuts: the handler closes over the current state.
    onMagnify { amount -> if (previewHovered) zoomBy((1 + amount).toFloat()) }
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
    // A body that parses as JSON and still cannot be DECODED — a string where a number goes — is not
    // handed to the frame: the frame's decode throws inside composition, and on the desktop that is a
    // dialog and a dead window rather than a red line. It is checked here, with the application's
    // own Json, and reported as a finding of the render layer; the frame keeps the last body it
    // could draw, the way it keeps it across a body that does not parse.
    LaunchedEffect(body) {
        if (parsed == null) return@LaunchedEffect
        delay(RENDER_DEBOUNCE_MS)
        val failure = runCatching { config.json.decodeKompotBody(body) }.exceptionOrNull()
        undecodable = failure?.let { it.message ?: it.toString() }
        if (failure == null) rendered = body
    }

    // The tree of the LAST BODY THAT PARSED. A keystroke in the middle of a string leaves the text
    // unparseable for as long as it takes to type the closing quote, and a tree that vanished for
    // that long — taking the selection and the inspector with it — would punish typing. The editor's
    // own strip says the body does not parse; the structure keeps showing what it last was.
    val parsedTree = remember(parsed, slots) { parsed?.let { screenTree(it, slots) } }
    val lastGoodTree = remember { mutableStateOf<ScreenNode?>(null) }
    SideEffect { if (parsedTree != null) lastGoodTree.value = parsedTree }
    val tree = parsedTree ?: lastGoodTree.value
    // The selection is a PATH, and the node is looked up in whatever tree the body has now. Keyed on
    // the tree it was a node, and every edit — the inspector's own included — rebuilt the tree and
    // dropped it, so the panel a person was typing into vanished under their caret. Under an
    // assistive client that was also a crash: Compose's accessibility sync cannot survive the
    // focused node being removed. A path outlives the edit; a node that is gone selects nothing.
    var selectedPath by remember(opened) { mutableStateOf<String?>(null) }
    // The node a drag is over, while it is: the preview frames it the way the tree tints it.
    var dropHover by remember { mutableStateOf<ScreenNode?>(null) }
    val selected = remember(tree, selectedPath) { selectedPath?.let { path -> tree?.flatten()?.firstOrNull { it.path == path } } }

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
        // The tree may be the last good one; an edit against text that does not parse has nothing
        // to land in.
        if (parsed == null) return
        val node = tree?.flatten()?.firstOrNull { it.path == targetPath } ?: return
        val target = dropTargetFor(node, slots) ?: return
        if (!target.replacing) {
            performDrop(payload, target)
            return
        }
        val incoming =
            Dragged.type(payload)
                ?: Dragged.path(payload)?.let { from -> tree.flatten().firstOrNull { it.path == from }?.label }
                ?: return
        val parent = tree.flatten().firstOrNull { it.path == target.parentPath }
        pendingDrop = PendingDrop(payload, target, parentLabel = parent?.label ?: target.parentPath, outgoing = node.label, incoming = incoming)
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
    var comparison by remember(body, brand, dark) { mutableStateOf<Comparison?>(null) }
    var captureStatus by remember(body, brand, dark) { mutableStateOf("") }
    // Whether the question about a stubbed frame is on screen. Reset with the body: the question is
    // about THIS screen, and carrying it across an edit would make the second capture the unguarded
    // one.
    var captureAsk by remember(body) { mutableStateOf(false) }

    val goldenFile: Path? =
        config.snapshotsDirectory?.resolve(
            config.goldenName(brand, dark, screen?.ref?.title ?: "screen"),
        )

    fun writeGolden(image: BufferedImage?) {
        val file = goldenFile ?: return
        if (image == null) {
            captureStatus = "nothing to capture"
            return
        }
        Files.createDirectories(file.parent)
        ImageIO.write(image, "png", file.toFile())
        comparison = null
        captureStatus = "wrote ${file.fileName}"
    }

    // Null when there was nothing to compare — no engine, no golden directory, no frame; the status
    // line says which. A missing golden is a RESULT, not an absence: the band offers to write one.
    fun compareToGolden(actual: BufferedImage?): Comparison? {
        val engine = capture ?: return null
        val file = goldenFile ?: return null
        if (actual == null) {
            captureStatus = "nothing to capture"
            return null
        }
        val expected = file.takeIf { Files.isRegularFile(it) }?.let { ImageIO.read(it.toFile()) } ?: return Comparison.NoGolden
        val diff = engine.diff(expected, actual)
        return if (diff.mismatchedPixels == 0) Comparison.Matches else Comparison.Differs(expected, actual, diff)
    }

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
        savedBody = body
    }
    // Unsaved is a fact about the text, not about the history: an edit undone back to the file is
    // not a change, and a file that changed underneath is not one either.
    val dirty = saveTo != null && body != savedBody

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

    // Painted, and it has to be: a Compose window on the desktop has no ground of its own, and the
    // light theme only ever looked right because AWT's default happened to be light. The first dark
    // theme drew light text on that same default.
    Column(Modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground)) {
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
            dirty = dirty,
            onSave = ::save,
            onExport = ::exportKotlin,
            note =
                listOf(
                    if (opened.isEmpty()) "demo · no sources — see the README for how to connect" else "",
                    if (dirty) "unsaved" else "",
                    captureStatus,
                    exported,
                ).filter { it.isNotEmpty() }.joinToString(" · "),
            capture =
                if (capture == null || goldenFile == null) {
                    null
                } else {
                    {
                        val safe = remember(config, body) { capturingIsSafe(config, body) }

                        Box {
                            OutlinedButton(onClick = {
                                // The frame this would write is drawn with a stubbed page loader, so
                                // it shows a list ending where it does not. Refusing outright would
                                // be wrong — somebody may want the picture anyway — but writing it
                                // silently is how a stub becomes a golden nobody remembers agreeing
                                // to. So: a question, at the button.
                                if (!safe) captureAsk = true else writeGolden(snap())
                            }) { IconLabel(StudioIcon.CAPTURE, if (safe) "Capture" else "Capture…") }

                            if (captureAsk) {
                                ConfirmPopup(
                                    title = AnnotatedString("Capture with a stubbed page loader?"),
                                    body =
                                        AnnotatedString(
                                            "The list in this frame ends where the stub ends, not where the " +
                                                "screen would. A frame like that shouldn't stand as the golden.",
                                        ),
                                    confirm = "Capture anyway",
                                    onConfirm = {
                                        captureAsk = false
                                        writeGolden(snap())
                                    },
                                    onCancel = { captureAsk = false },
                                    alignment = Alignment.BottomStart,
                                )
                            }
                        }

                        OutlinedButton(onClick = { comparison = compareToGolden(snap()) }) { IconLabel(StudioIcon.COMPARE, "Compare") }
                        Divider(Orientation.Vertical, Modifier.height(20.dp))
                    }
                },
        )
        HRule()

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
                    dropKindOf = { node -> dropTargetFor(node, slots)?.kindFor(node) },
                    onHover = { dropHover = it },
                    onDemo = { apply(SAMPLE_BODY) },
                    selectedPath = selected?.path,
                    marks = marks,
                    paletteCount = paletteCount,
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
                            ConfirmPopup(
                                title = replaceTitle(drop),
                                body = replaceBody(drop),
                                confirm = "Replace",
                                onConfirm = {
                                    performDrop(drop.payload, drop.target)
                                    pendingDrop = null
                                },
                                onCancel = { pendingDrop = null },
                            )
                        }
                    },
                    edits = {
                        EditRow(
                            enabled = selected != null && parsed != null,
                            onMoveUp = { apply(JsonEdits.moveUp(body, selected!!.path)) },
                            onMoveDown = { apply(JsonEdits.moveDown(body, selected!!.path)) },
                            onDuplicate = { apply(JsonEdits.duplicate(body, selected!!.path)) },
                            onDelete = { apply(JsonEdits.delete(body, selected!!.path)) },
                        )
                    },
                ) { selectedPath = it.path }
            },
            second = {
                Row(Modifier.fillMaxSize()) {
                VRule()
                Column(Modifier.fillMaxSize()) {
                    HorizontalSplitLayout(
                        first = {
                            Column(Modifier.fillMaxSize()) {
                                BodyEditor(
                                    state = bodyState,
                                    lexed = lexed,
                                    errorOffset = findings.firstOrNull { it.layer == "syntax" }?.offset,
                                    // On the field ground, not the panel's: the text is the one
                                    // thing in the window that is typed into, and it sits a shade
                                    // deeper than the panels around it, the way every editor's does.
                                    modifier = Modifier.fillMaxWidth().weight(1f).background(studioColors().field).padding(8.dp),
                                    selectedRange = selected?.path?.let { lexed.spans[it] },
                                )

                                findings.firstOrNull { it.layer == "syntax" }?.let { ParseErrorStrip(it) }

                                // Under the text and not under the whole window: it is about one node
                                // of this body, and it takes its height from nothing that stretches.
                                // Only over a body that parses — the tree may be showing the last good
                                // one, and a field edited against text with no such node writes
                                // nowhere.
                                val node = selected?.takeIf { parsed != null }
                                if (node != null) {
                                    HRule()
                                    InspectorPane(
                                        config = config,
                                        node = node,
                                        body = body,
                                        brand = brand,
                                        dark = dark,
                                        modifier = Modifier.fillMaxWidth().height(INSPECTOR_HEIGHT),
                                    ) { edited ->
                                        history.record(edited)
                                        bodyState.setTextAndPlaceCursorAtEnd(edited)
                                    }
                                }
                            }
                        },
                        second = {
                            Row(Modifier.fillMaxSize()) {
                            VRule()
                            val colors = studioColors()
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .background(if (JewelTheme.isDark) colors.field else colors.hover)
                                    .onPointerEvent(PointerEventType.Enter) { previewHovered = true }
                                    .onPointerEvent(PointerEventType.Exit) { previewHovered = false }
                                    // Cmd (or Ctrl) and the wheel, for a mouse and for anybody whose
                                    // runtime has no pinch to offer.
                                    .onPointerEvent(PointerEventType.Scroll) { event ->
                                        if (event.keyboardModifiers.isMetaPressed || event.keyboardModifiers.isCtrlPressed) {
                                            val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                            if (dy != 0f) zoomBy(1 - dy * WHEEL_ZOOM)
                                        }
                                    }
                                    .padding(16.dp, 16.dp, 16.dp, 10.dp),
                            ) {
                            val subject =
                                listOfNotNull(screen?.ref?.title, brand, if (dark) "dark" else "light").joinToString(" · ")
                            var frames by remember { mutableStateOf(true) }
                            comparison?.let { result ->
                                ComparisonBand(
                                    result = result,
                                    subject = subject,
                                    frames = frames,
                                    onFrames = { frames = it },
                                    onHide = { comparison = null },
                                    onAccept = { writeGolden((result as? Comparison.Differs)?.actual) },
                                    onCapture = { writeGolden(snap()) },
                                )
                            }
                            val differs = comparison as? Comparison.Differs
                            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(GAP)) {
                                if (differs != null && frames) {
                                    // Three pictures instead of the live frame: what was expected,
                                    // what is drawn, and where they disagree.
                                    Frame("golden", differs.expected)
                                    Frame("current", differs.actual)
                                    Frame("mask", differs.diff.image)
                                } else {
                                // Beside the frame rather than instead of it: what a golden disagrees
                                // about is only readable next to what the screen actually draws.
                                if (differs != null) {
                                    Image(
                                        bitmap = differs.diff.image.toComposeImageBitmap(),
                                        contentDescription = "the pixels a golden disagrees about",
                                        modifier = Modifier.weight(1f),
                                    )
                                }

                                val picked = fixture
                                if (picked != null) {
                                    // Inside the consumer's frame like anything else: a fixture drawn
                                    // outside the brand would be a picture of a composition nobody ships.
                                    DeviceFrame(device, Modifier.weight(1f).fillMaxSize(), zoom, { shownScale = it }) {
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
                                        dropId = dropHover?.id,
                                        state = previewState,
                                        device = device,
                                        zoom = zoom,
                                        onScale = { shownScale = it },
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
                            }
                            // What is being looked at, in the caption's own words: the size and the
                            // brand and theme the frame was asked for — so a screenshot of the window
                            // carries its own provenance.
                            Row(Modifier.fillMaxWidth().padding(top = 4.dp).height(22.dp), verticalAlignment = Alignment.CenterVertically) {
                                Mono(device.label.lowercase(), colors.dim)
                                Spacer(Modifier.weight(1f))
                                // Stepping from the zoom that is SET when one is, and from the
                                // drawn scale only when fitting: the drawn scale arrives a frame
                                // late, and two quick clicks from it are one step, not two.
                                ZoomControl(
                                    scale = zoom ?: shownScale,
                                    fitted = zoom == null,
                                    onZoom = { zoom = it },
                                    onReset = { zoom = null },
                                )
                                Spacer(Modifier.weight(1f))
                                Mono(listOfNotNull(brand, if (dark) "dark" else "light").joinToString(" · "), colors.dim)
                            }
                            }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        firstPaneMinWidth = 320.dp,
                        secondPaneMinWidth = 320.dp,
                        state = mainSplit,
                    )

                    HRule()
                    Drawer(
                        findings = findings + degradations,
                        actions = actions,
                        opened = opened,
                        labels = remember(tree) { tree?.flatten()?.associate { it.path to it.label } ?: emptyMap() },
                        brand = brand,
                        onFinding = { finding ->
                            // Clicking a finding selects the node it is about — the two carry the same
                            // notation, so the join is an equality rather than a parse. A finding with
                            // no node (a syntax error, a degradation that names only a type) selects
                            // nothing rather than guessing.
                            finding.path?.let { selectedPath = it }
                        },
                        onOffset = { offset -> bodyState.edit { selection = TextRange(offset.coerceIn(0, length)) } },
                        onNavigate = { screen = it },
                    )
                }
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
    onLoaded: (String) -> Unit,
): String {
    // Nothing, rather than "no screen selected": the toolbar title already says so, and the same
    // words twice one above the other read as a stutter.
    if (selected == null) return ""

    val state by opened[selected.source].session.body(selected.ref).collectAsState()

    LaunchedEffect(selected, state.revisions) {
        state.text?.let {
            bodyState.setTextAndPlaceCursorAtEnd(it)
            onLoaded(it)
        }
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
    val parentLabel: String,
    val outgoing: String,
    val incoming: String,
)

// The question, with the names in the reading colour and the sentence around them in the dim one:
// what goes and what comes are the two things the answer depends on.
private fun replaceTitle(drop: PendingDrop): AnnotatedString =
    buildAnnotatedString {
        append("Replace the node in ")
        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Normal)) {
            append("${drop.parentLabel} › ${drop.target.slot}")
        }
        append("?")
    }

@Composable
private fun replaceBody(drop: PendingDrop): AnnotatedString {
    val named = SpanStyle(color = studioColors().text)
    return buildAnnotatedString {
        append("This slot holds one node. ")
        withStyle(named) { append(drop.outgoing) }
        append(" will be removed; ")
        withStyle(named) { append(drop.incoming) }
        append(" takes its place. Cmd+Z undoes.")
    }
}

// WHAT A COMPARE FOUND. Three answers and not a number: a number is what the band prints for one of
// them, and the other two — "the same" and "nothing to compare against" — each have their own move.
private sealed interface Comparison {
    class Differs(
        val expected: BufferedImage,
        val actual: BufferedImage,
        val diff: FrameDiff,
    ) : Comparison

    data object Matches : Comparison

    data object NoGolden : Comparison
}

// The result, above the frame it is about, with the one thing to do about it at the right end:
// accept the current frame, write the first golden, or put the band away.
@Composable
private fun ComparisonBand(
    result: Comparison,
    subject: String,
    frames: Boolean,
    onFrames: (Boolean) -> Unit,
    onHide: () -> Unit,
    onAccept: () -> Unit,
    onCapture: () -> Unit,
) {
    val colors = studioColors()
    when (result) {
        // Two lines where the design has one: the preview column is half the width of the
        // designer's frame, and a button squeezed to its first letter is not a button.
        is Comparison.Differs ->
            Column(Modifier.fillMaxWidth().padding(bottom = GAP), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(GAP), verticalAlignment = Alignment.CenterVertically) {
                    Icon(StudioIcon.COMPARE, colors.error)
                    Text("Differs from golden")
                    Mono("${"%.2f".format(result.diff.mismatchPercent)}% · ${result.diff.mismatchedPixels} px", colors.error)
                    Dim("· $subject", Modifier.weight(1f))
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(GAP, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SmallSegmented(listOf(FRAMES, MASK), if (frames) FRAMES else MASK) { onFrames(it == FRAMES) }
                    OutlinedButton(onClick = onAccept) { Text("Accept as golden") }
                }
            }

        Comparison.Matches ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = GAP),
                horizontalArrangement = Arrangement.spacedBy(GAP),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(StudioIcon.OK, colors.ok)
                Text("Matches golden")
                Dim("· $subject", Modifier.weight(1f))
                OutlinedButton(onClick = onHide) { Text("Hide") }
            }

        Comparison.NoGolden ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = GAP)
                    .drawBehind {
                        drawRoundRect(
                            colors.controlLine,
                            cornerRadius = CornerRadius(8.dp.toPx()),
                            style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))),
                        )
                    }
                    .padding(horizontal = GUTTER, vertical = GAP),
                horizontalArrangement = Arrangement.spacedBy(GAP),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(StudioIcon.DRAFT, colors.dim)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("No golden yet")
                    Dim("Capture the current frame to make it the golden for $subject.")
                }
                DefaultButton(onClick = onCapture) { Text("Capture as golden") }
                OutlinedButton(onClick = onHide) { Text("Hide") }
            }
    }
}

private const val FRAMES = "3 frames"
private const val MASK = "mask"

// One of the three pictures a difference is made of, with its name under it. Fit rather than
// stretched: three frames side by side are narrower than one, and a picture with a different
// aspect from its neighbours is a picture of a different screen.
@Composable
private fun RowScope.Frame(
    caption: String,
    image: BufferedImage,
) {
    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            bitmap = image.toComposeImageBitmap(),
            contentDescription = caption,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentScale = ContentScale.Fit,
        )
        Mono(caption, studioColors().dim, Modifier.padding(top = 4.dp))
    }
}

// Minus, the percentage, plus. The percentage is the reset: it says what the frame is drawn at, and
// clicking it goes back to fitting the pane — "fit" beside the number when that is already the case.
@Composable
private fun ZoomControl(
    scale: Float,
    fitted: Boolean,
    onZoom: (Float) -> Unit,
    onReset: () -> Unit,
) {
    val colors = studioColors()
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        ZoomButton(StudioIcon.MINUS) { onZoom((scale / ZOOM_STEP).coerceAtLeast(MIN_ZOOM)) }
        Mono(
            "${(scale * 100).roundToInt()}%" + if (fitted) " · fit" else "",
            colors.dim,
            Modifier.focusProperties { canFocus = false }.clickable(onClick = onReset).padding(horizontal = 4.dp),
        )
        ZoomButton(StudioIcon.ADD) { onZoom((scale * ZOOM_STEP).coerceAtMost(MAX_ZOOM)) }
    }
}

@Composable
private fun ZoomButton(
    icon: StudioIcon,
    onClick: () -> Unit,
) {
    Icon(
        icon,
        studioColors().dim,
        Modifier.focusProperties { canFocus = false }.clickable(onClick = onClick).padding(2.dp),
    )
}

private const val ZOOM_STEP = 1.25f
private const val WHEEL_ZOOM = 0.05f
private const val MIN_ZOOM = 0.25f
private const val MAX_ZOOM = 4f

// The line under the text when the text does not parse: where, and what the rest of the window is
// showing meanwhile. The parser's own words after it, for whoever wants them.
@Composable
private fun ParseErrorStrip(finding: Finding) {
    val colors = studioColors()
    Row(
        Modifier.fillMaxWidth().background(colors.error.copy(alpha = 0.12f)).padding(horizontal = GUTTER, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(StudioIcon.ERROR, colors.error)
        val where = finding.offset?.let { " at offset $it" } ?: ""
        Text("Body doesn't parse$where — tree and preview show the last good body.", maxLines = 1, overflow = TextOverflow.Ellipsis)
        Dim(finding.message, Modifier.weight(1f))
    }
}

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
private val INSPECTOR_HEIGHT = 300.dp
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
    dirty: Boolean,
    onSave: () -> Unit,
    onExport: () -> Unit,
    note: String,
    capture: (@Composable RowScope.() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth().height(40.dp).padding(horizontal = GUTTER),
        horizontalArrangement = Arrangement.spacedBy(GUTTER),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            // The status line, and it is where the sources pay for themselves: "polled 42, changed 1"
            // is what a working ETag looks like, and "polled 42, changed 42" is a server that ignores
            // If-None-Match. Both draw the same screen, so nothing else in this window can tell them
            // apart. What the actions reported goes on the same line, after a dot.
            val line = listOf(status, note).filter { it.isNotEmpty() }.joinToString(" · ")
            if (line.isNotEmpty()) Dim(line, Modifier.weight(1f, fill = false))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(GUTTER), verticalAlignment = Alignment.CenterVertically) {
            // Named, so the theme switch beside it reads as the preview's and not the window's.
            Dim("preview")
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
            capture?.invoke(this)
            // Filled while there is something to save and outlined once there is not: the one button
            // whose state a person needs to read from across the room.
            if (dirty) {
                DefaultButton(onClick = onSave) { Text("Save") }
            } else {
                OutlinedButton(onClick = onSave, enabled = canSave) { Text("Save") }
            }
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
private fun IconLabel(
    icon: StudioIcon,
    label: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, studioColors().text)
        Text(label)
    }
}

@Composable
private fun Mono(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Text(text, modifier, color = color, fontFamily = FontFamily.Monospace, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    action: (@Composable () -> Unit)? = null,
    expanded: Boolean? = null,
    onToggle: (() -> Unit)? = null,
) {
    val colors = studioColors()
    Row(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .focusProperties { canFocus = false }
            .then(if (onToggle == null) Modifier else Modifier.clickable(onClick = onToggle))
            .padding(start = if (expanded == null) GUTTER else GAP, end = GUTTER),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (expanded != null) Icon(if (expanded) StudioIcon.CHEVRON_DOWN else StudioIcon.CHEVRON_RIGHT, colors.dim)
        Text(title, fontWeight = FontWeight.Medium)
        if (detail != null) Dim(detail, Modifier.weight(1f, fill = false))
        Spacer(Modifier.weight(1f))
        if (trailing != null) Dim(trailing)
        action?.invoke()
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
    dropKindOf: (ScreenNode) -> DropKind?,
    onHover: (ScreenNode?) -> Unit,
    onDemo: () -> Unit,
    selectedPath: String?,
    marks: Map<String, Severity>,
    paletteCount: Int,
    palette: @Composable () -> Unit,
    pending: @Composable () -> Unit,
    edits: @Composable () -> Unit,
    onNode: (ScreenNode) -> Unit,
) {
    val colors = studioColors()
    var screensOpen by remember { mutableStateOf(true) }
    var paletteOpen by remember { mutableStateOf(true) }
    var searching by remember { mutableStateOf(false) }
    val query = rememberTextFieldState()

    Column(Modifier.fillMaxSize()) {
        val recorded = opened.map { source -> source.session.screens.collectAsState().value }
        val total = recorded.sumOf { it.size } + stories.size + fixtures.size
        if (total > 0) {
            SectionHeader(
                "Screens",
                trailing = total.toString(),
                action = {
                    // A filter, opened by the glyph and closed by it: a list of forty recorded screens
                    // is scrolled once and searched every time after.
                    Icon(
                        StudioIcon.SEARCH,
                        if (searching) colors.text else colors.dim,
                        Modifier.focusProperties { canFocus = false }.clickable {
                            searching = !searching
                            if (!searching) query.clearText()
                        },
                    )
                },
                expanded = screensOpen,
                onToggle = { screensOpen = !screensOpen },
            )
            if (screensOpen) {
                if (searching) {
                    TextField(
                        state = query,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = GUTTER, vertical = 4.dp),
                        placeholder = { Text("Filter screens") },
                    )
                }
                ScreensPane(
                    opened = opened,
                    recorded = recorded,
                    selected = screen,
                    stories = stories,
                    fixtures = fixtures,
                    query = query.text.toString(),
                    onSelect = onScreen,
                    onStory = onStory,
                    onFixture = onFixture,
                    modifier = Modifier.fillMaxWidth().heightIn(max = SCREENS_MAX_HEIGHT),
                )
            }
        }

        HRule()
        SectionHeader("Structure", trailing = tree?.flatten()?.size?.let { "$it nodes" })
        ScreenTreePane(
            root = tree,
            modifier = Modifier.fillMaxWidth().weight(1f),
            selectedPath = selectedPath,
            marks = marks,
            onDrop = onDrop,
            dropKindOf = dropKindOf,
            onHover = onHover,
            empty = {
                EmptyState(
                    StudioIcon.COLUMN,
                    "No screen selected",
                    "Pick one in Screens above, or open the toolkit demo.",
                    action = "Open demo screen",
                    onAction = onDemo,
                )
            },
            onSelect = onNode,
        )
        pending()
        edits()

        HRule()
        SectionHeader("Palette", trailing = "$paletteCount types", expanded = paletteOpen, onToggle = { paletteOpen = !paletteOpen })
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
        EditButton(StudioIcon.MOVE_UP, "Move up", enabled, onMoveUp)
        EditButton(StudioIcon.MOVE_DOWN, "Move down", enabled, onMoveDown)
        EditButton(StudioIcon.DUPLICATE, "Duplicate", enabled, onDuplicate)
        EditButton(StudioIcon.DELETE, "Delete", enabled, onDelete)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EditButton(
    icon: StudioIcon,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = studioColors()
    Tooltip(tooltip = { Text(label) }) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(28.dp)) {
            Icon(icon, if (enabled) colors.text else colors.dim)
        }
    }
}

// The lists on the left, one group per kind of body: what a deployment recorded, what the profile's
// samples say a component looks like, what viddik draws for a screenshot. Five rows of each and a
// "N more" after them, because the group somebody is not looking for should take five lines, not
// forty.
@Composable
private fun ScreensPane(
    opened: List<OpenSource>,
    recorded: List<List<ScreenRef>>,
    selected: SelectedScreen?,
    stories: List<Story>,
    fixtures: List<ViddikStory>,
    query: String,
    onSelect: (SelectedScreen) -> Unit,
    onStory: (Story) -> Unit,
    onFixture: (ViddikStory) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(bottom = 4.dp)) {
        opened.forEachIndexed { index, source ->
            val rows =
                recorded[index].filter { it.title.contains(query, ignoreCase = true) }.map { ref ->
                    ScreenRow(
                        icon = StudioIcon.SCREEN,
                        text = if (ref.kind == "screen") ref.title else "${ref.title} · ${ref.kind}",
                        selected = selected?.source == index && selected.ref == ref,
                    ) { onSelect(SelectedScreen(index, ref)) }
                }
            ScreenGroup(if (opened.size > 1) source.name else "Recorded", rows)
        }

        // A story with no body is listed and does nothing: the gap is the message, and a row that
        // vanished would answer "which component has nobody drawn" with silence.
        ScreenGroup(
            "Stories",
            stories.filter { "${it.group} ${it.name}".contains(query, ignoreCase = true) }.map { story ->
                ScreenRow(
                    icon = StudioIcon.STORY,
                    text = "${story.group} · ${story.name}" + if (story.body == null) "  (no sample)" else "",
                    selected = false,
                    onClick = if (story.body == null) null else ({ onStory(story) }),
                )
            },
        )

        ScreenGroup(
            "Fixtures",
            fixtures.filter { "${it.group}_${it.name}".contains(query, ignoreCase = true) }.map { story ->
                ScreenRow(StudioIcon.CAPTURE, "${story.group}_${story.name}", selected = false) { onFixture(story) }
            },
        )
    }
}

private class ScreenRow(
    val icon: StudioIcon,
    val text: String,
    val selected: Boolean,
    val onClick: (() -> Unit)?,
)

@Composable
private fun ScreenGroup(
    title: String,
    rows: List<ScreenRow>,
) {
    if (rows.isEmpty()) return
    var all by remember(title) { mutableStateOf(false) }
    val colors = studioColors()

    Row(
        Modifier.fillMaxWidth().height(24.dp).padding(horizontal = GUTTER),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title.uppercase(), color = colors.dim, fontSize = 11.sp, letterSpacing = 0.04.em)
        Text(rows.size.toString(), color = colors.dim, fontSize = 11.sp)
    }

    // The selected row is always among the shown: a list that hid what is picked would make the
    // highlight in the preview point at nothing on the left.
    val picked = rows.indexOfFirst { it.selected }
    val shown = if (all || rows.size <= VISIBLE_ROWS + 1 || picked >= VISIBLE_ROWS) rows else rows.take(VISIBLE_ROWS)
    shown.forEach { ListRow(it) }
    if (shown.size < rows.size) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .focusProperties { canFocus = false }
                .clickable { all = true }
                .padding(start = ROW_INDENT, end = GUTTER),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${rows.size - shown.size} more", color = colors.dim)
            Icon(StudioIcon.CHEVRON_RIGHT, colors.dim)
        }
    }
}

// One row for every list on the left, so a screen, a story and a fixture are picked the same way and
// look picked the same way.
@Composable
private fun ListRow(row: ScreenRow) {
    val colors = studioColors()
    Row(
        Modifier
            .fillMaxWidth()
            .height(24.dp)
            .then(if (row.selected) Modifier.background(colors.selection) else Modifier)
            .focusProperties { canFocus = false }
            .then(if (row.onClick == null) Modifier else Modifier.clickable(onClick = row.onClick))
            .padding(start = ROW_INDENT, end = GUTTER),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(row.icon, if (row.selected) colors.text else colors.dim)
        Text(
            row.text,
            Modifier.weight(1f),
            color = if (row.onClick == null) colors.dim else colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (row.selected) Box(Modifier.size(6.dp).background(colors.accent, CircleShape))
    }
}

private const val VISIBLE_ROWS = 5
private val ROW_INDENT = 30.dp

private enum class DrawerTab { FINDINGS, ACTIONS }

// The layers a finding can come from, as the filter names them. `rules:<name>` folds into "rules":
// the rule's own name is on the row, and a filter per rule would be a list as long as the profile.
private val LAYER_FILTERS = listOf("all", "schema", "rules", "vocabulary", "render", "draft")

private fun layerGroup(layer: String): String = layer.substringBefore(':')

// One row of the findings list. Mostly a finding as it came, except for drafts: a node added from the
// palette raises one "required and empty" per field, and five rows that all say "you have not filled
// this in yet" about one node are one row's worth of information.
private data class DrawerEntry(
    val path: String?,
    val message: String,
    val layer: String,
    val severity: Severity,
    val draft: Boolean,
    val offset: Int?,
    val finding: Finding,
) {
    val key: String get() = "$layer|$path|$message"
}

private val REQUIRED_FIELD = Regex("\"([^\"]+)\" is required")

private fun entriesOf(findings: List<Finding>): List<DrawerEntry> {
    val (drafts, rest) = findings.partition { it.layer == "draft" }
    val plain =
        rest.map { DrawerEntry(it.path, it.message, it.layer, it.severity, draft = false, offset = it.offset, finding = it) }
    val grouped =
        drafts.groupBy { it.path }.map { (path, group) ->
            val names = group.mapNotNull { REQUIRED_FIELD.find(it.message)?.groupValues?.get(1) }
            val message =
                if (names.size == group.size && names.isNotEmpty()) {
                    val fields = if (names.size == 1) "field" else "fields"
                    "${names.size} required $fields empty: ${names.joinToString(", ")} — node added but not filled in"
                } else {
                    group.joinToString("; ") { it.message }
                }
            DrawerEntry(path, message, "draft", group.first().severity, draft = true, offset = null, finding = group.first())
        }
    // Errors, then warnings, then drafts: a degradation is the protocol working as designed, and a
    // page of them above the one line that says the body is malformed buries it; a draft is not even
    // a defect yet.
    return (plain + grouped).sortedWith(compareBy({ it.draft }, { it.severity.ordinal }))
}

// What the tool has to say, in one place at the bottom, with counts on the tabs so a closed drawer
// still says whether there is anything in it. Findings first: an action is something that happened,
// a finding is something wrong.
@Composable
private fun Drawer(
    findings: List<Finding>,
    actions: List<LoggedAction>,
    opened: List<OpenSource>,
    labels: Map<String, String>,
    brand: String?,
    onFinding: (Finding) -> Unit,
    onOffset: (Int) -> Unit,
    onNavigate: (SelectedScreen) -> Unit,
) {
    val colors = studioColors()
    var tab by remember { mutableStateOf(DrawerTab.FINDINGS) }
    var open by remember { mutableStateOf(true) }
    var layer by remember { mutableStateOf("all") }
    var expanded by remember { mutableStateOf<String?>(null) }
    val entries = remember(findings) { entriesOf(findings) }
    val errors = entries.count { it.severity == Severity.ERROR }
    val drafts = entries.count { it.draft }
    val warnings = entries.size - errors - drafts

    Row(
        Modifier.fillMaxWidth().height(32.dp).padding(start = GAP, end = GUTTER),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (open) StudioIcon.CHEVRON_DOWN else StudioIcon.CHEVRON_RIGHT,
            colors.dim,
            Modifier.focusProperties { canFocus = false }.clickable { open = !open },
        )
        // Three weights on one tab, in the order the list has them: what is wrong, what draws badly,
        // what is not filled in yet.
        DrawerTabLabel("Findings", tab == DrawerTab.FINDINGS, onClick = { tab = DrawerTab.FINDINGS; open = true }) {
            if (errors > 0) Badge(errors.toString(), colors.error, androidx.compose.ui.graphics.Color.White)
            if (warnings > 0) Badge(warnings.toString(), colors.warning, androidx.compose.ui.graphics.Color(0xFF1E1F22))
            if (drafts > 0) Badge(drafts.toString(), colors.badge, colors.badgeText)
        }
        DrawerTabLabel("Actions", tab == DrawerTab.ACTIONS, onClick = { tab = DrawerTab.ACTIONS; open = true }) {
            if (actions.isNotEmpty()) Badge(actions.size.toString(), colors.badge, colors.badgeText)
        }
        Spacer(Modifier.weight(1f))
        if (tab == DrawerTab.FINDINGS && entries.isNotEmpty()) {
            SmallSegmented(LAYER_FILTERS, layer) { layer = it }
        }
    }
    HRule()

    if (!open) return

    Column(
        Modifier.fillMaxWidth().heightIn(min = 44.dp, max = DRAWER_MAX_HEIGHT).verticalScroll(rememberScrollState()).padding(vertical = 4.dp),
    ) {
        when (tab) {
            DrawerTab.FINDINGS -> {
                val shown = if (layer == "all") entries else entries.filter { layerGroup(it.layer) == layer }
                if (entries.isEmpty()) {
                    DrawerRow(StudioIcon.OK, colors.ok) {
                        Text("No findings — the body is what ${brand ?: "this build's profile"} expects.", color = colors.dim)
                    }
                } else if (shown.isEmpty()) {
                    DrawerRow(StudioIcon.INFO, colors.dim) {
                        Text("Nothing from the $layer layer — ${entries.size} elsewhere.", color = colors.dim)
                    }
                }
                // Keyed, so that a filter removes the rows it removes and leaves the others' nodes
                // alone: positional reuse would turn "the render row went away" into "every row
                // below it was torn down and rebuilt", and a row torn down under an assistive
                // client's focus is the crash the tree rows were made non-focusable for.
                shown.forEach { entry ->
                    key(entry.key) {
                        FindingRow(
                        entry = entry,
                        label = entry.path?.let { labels[it] },
                        expanded = expanded == entry.key,
                        onClick = {
                            expanded = if (expanded == entry.key) null else entry.key
                            onFinding(entry.finding)
                        },
                        onOffset = onOffset,
                        )
                    }
                }
            }

            DrawerTab.ACTIONS -> {
                if (actions.isEmpty()) {
                    DrawerRow(StudioIcon.INFO, colors.dim) {
                        Text("Nothing tapped yet — tap a button in the preview to log its action.", color = colors.dim)
                    }
                }
                // The one line in the log the studio can act on: a navigate names a deeplink, and an
                // HTTP source has already read the graph that maps deeplinks to endpoints. Clicking it
                // opens that screen.
                actions.asReversed().forEach { logged ->
                    val target = logged.deeplink?.let { deeplink -> routeFor(opened, deeplink) }
                    DrawerRow(
                        if (target == null) StudioIcon.INFO else StudioIcon.NAVIGATE,
                        colors.dim,
                        if (target == null) Modifier else Modifier.clickable { onNavigate(target) },
                    ) {
                        Text(logged.text, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

// A finding as one line — glyph, where, what, which layer — and, once clicked, the same line with the
// whole message under it and the two things there are to do with one. Expanded in place rather than
// in a tooltip: a message long enough to be cut is one somebody will want to copy.
@Composable
private fun FindingRow(
    entry: DrawerEntry,
    label: String?,
    expanded: Boolean,
    onClick: () -> Unit,
    onOffset: (Int) -> Unit,
) {
    val colors = studioColors()
    val (icon, tint) =
        when {
            entry.draft -> StudioIcon.DRAFT to colors.dim
            entry.severity == Severity.ERROR -> StudioIcon.ERROR to colors.error
            else -> StudioIcon.WARNING to colors.warning
        }
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (expanded) Modifier.background(colors.selection) else Modifier)
            .focusProperties { canFocus = false }
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.fillMaxWidth().height(24.dp).padding(horizontal = GUTTER),
            horizontalArrangement = Arrangement.spacedBy(GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, tint)
            val where = listOfNotNull(entry.path, label).joinToString(" · ").ifEmpty { "—" }
            Mono(where, colors.dim, Modifier.width(FINDING_PATH_WIDTH))
            Text(entry.message, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entry.layer, Modifier.width(FINDING_LAYER_WIDTH), color = colors.dim, textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (expanded) {
            Column(
                Modifier.fillMaxWidth().padding(start = GUTTER + 16.dp + GAP, end = GUTTER, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(entry.message)
                Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                    if (entry.offset != null) {
                        OutlinedButton(onClick = { onOffset(entry.offset) }) { Text("Go to offset ${entry.offset}") }
                    }
                    OutlinedButton(onClick = { copyToClipboard(entry.message) }) { Text("Copy message") }
                }
            }
        }
    }
}

// AWT's clipboard rather than Compose's: the Compose one is a suspend API behind a deprecation
// notice, and copying a line of text is not an operation that needs a coroutine.
private fun copyToClipboard(text: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}

private val FINDING_PATH_WIDTH = 168.dp
private val FINDING_LAYER_WIDTH = 72.dp

@Composable
private fun DrawerTabLabel(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    badges: @Composable RowScope.() -> Unit,
) {
    val colors = studioColors()
    // As wide as its label, and no wider: a tab that filled the row would push the next one out.
    Column(Modifier.fillMaxHeight().width(IntrinsicSize.Max).focusProperties { canFocus = false }.clickable(onClick = onClick)) {
        Row(
            Modifier.weight(1f).padding(horizontal = GAP),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = if (active) colors.text else colors.dim)
            badges()
        }
        Spacer(Modifier.fillMaxWidth().height(2.dp).background(if (active) colors.accent else androidx.compose.ui.graphics.Color.Transparent))
    }
}

@Composable
private fun Badge(
    text: String,
    background: androidx.compose.ui.graphics.Color,
    foreground: androidx.compose.ui.graphics.Color,
) {
    Text(
        text,
        Modifier.background(background, RoundedCornerShape(8.dp)).padding(horizontal = 5.dp),
        color = foreground,
        fontSize = 11.sp,
    )
}

@Composable
private fun DrawerRow(
    icon: StudioIcon,
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier.fillMaxWidth().height(24.dp).padding(horizontal = GUTTER),
        horizontalArrangement = Arrangement.spacedBy(GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, tint)
        content()
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

private const val ACCESSIBILITY_PROPERTY = "compose.accessibility.enable"
private const val SHOW_TIMEOUT_MS = 10_000L
private const val POLL_MS = 100L
