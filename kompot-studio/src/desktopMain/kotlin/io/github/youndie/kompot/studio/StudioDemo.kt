package io.github.youndie.kompot.studio

import io.github.youndie.kompot.KompotRegistry
import io.github.youndie.kompot.kompotCoreRenderers
import io.github.youndie.kompot.kompotStandardRenderers

// THE STUDIO WITHOUT A CONSUMER: the toolkit's own renderers, the toolkit's own schemas, the default
// frame. It is what `./gradlew :kompot-studio:run` opens, and it is also the shortest possible
// example of what a deployment writes — swap kompotStandardRenderers for your registry, add your
// frame, and that is the whole integration.
//
// It stays in the published module rather than in a sample source set on purpose: the module ships an
// application and a library at once, the way :kompot-preview ships a harness and an IDE preview of it.
//
// ─── B-08, THE SPIKE, AND ITS FIVE ANSWERS ───
//
// The research took three decisions on paper: that Jewel's shell and the toolkit's material3
// renderers live in one window, that KompotPreview draws inside a Jewel split without losing its
// MaterialTheme, and that Compose Hot Reload redraws a renderer edited in place. Each could be false
// for a reason only a running window shows — the way skiko's poisoned Surface class was, next door in
// IdePreviewExperiment.kt. The five questions, and what running this answered:
//
// (1) DOES THE WINDOW OPEN ON A JBR AND ON A PLAIN JDK? — YES to both, and the second only because
//     kompotStudio() branches. Research §5.5 said DecoratedWindow "degrades to an ordinary window" on
//     a non-JetBrains runtime. It does not: its first statement is `if (!JBR.isAvailable()) error(...)`.
//     The decorated path was watched opening — `window showing=true size=1280x803 decorated=true
//     jbr=true vendorVersion=JBR-25.0.4.1` — and JetBrainsRuntimeTest keeps the gate asserted on a
//     machine with no screen; the plain JDK path was watched opening on OpenJDK 25.
//
//     The runtime is 25 and that number is Jewel's, not ours: 0.40 ships class file 69, and a JBR 21
//     died at class loading on JewelTheme long after everything had compiled and its tests had passed.
//     The published bytecode stays at the toolkit's floor of 17.
//
// (2) IS A KOMPOT BUTTON DRAWN IN MaterialTheme's COLOURS OR JEWEL's? — Material's. Asserted rather
//     than looked at: the capture tests read a brand's colour out of a frame, and those colours exist
//     nowhere in the Jewel palette. The boundary is the frame — the render pane sits inside the
//     consumer's MaterialTheme, the chrome inside IntUiTheme.
//
// (3) DOES HOT RELOAD REDRAW AN EDITED RENDERER? — STILL OPEN, and the one answer the spike does not
//     have. Both preconditions are in place and were not assumed: the Compose plugin registers
//     `hotRunDesktop`, `hotReloadDesktopMain` and `reload` for this module out of the box, and `run`
//     executes on a JetBrains Runtime. What is missing is the part that cannot be automated here — a
//     session with a screen, where somebody edits ButtonRenderer and watches the frame.
//
// (4) DOES captureComposable TAKE THE FRAME THE WINDOW SHOWS? — Yes, and that is why the render pane
//     is StudioRenderPane in a file of its own: the window and the tests compose the same function.
//
// (5) DOES A paginated_list BODY FAIL? — Yes: LocalKompotPageLoader is not provided by KompotPreview,
//     and the second sample dies inside the render with "not provided". Recorded, not fixed — the
//     parameter is B-02's, and a spike that fixed what it found would stop being a measurement.

internal val toolkitRegistry: KompotRegistry = KompotRegistry(kompotCoreRenderers + kompotStandardRenderers)

internal fun sample(name: String): String =
    checkNotNull(object {}.javaClass.getResourceAsStream("/$name")) { "the sample $name is not in the resources" }
        .use { it.readBytes().decodeToString() }

internal val SAMPLE_BODY: String get() = sample("sample-screen.json")

public fun main() {
    kompotStudio(KompotStudioConfig(registry = toolkitRegistry))
}
