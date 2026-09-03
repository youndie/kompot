plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    id("ru.workinprogress.sborka.kmp")
    id("ru.workinprogress.sborka.publish")
}

// The only module here that is an APPLICATION rather than a library, and the only one with a single
// target. Both follow from what it renders with: the studio draws a screen through the consumer's own
// renderers, which means it lives in the consumer's classpath and on the desktop JVM — the one place
// where the whole toolkit, a real Compose runtime and a file system meet. Publishing it is B-09's
// question; this module answers B-08's, which is whether the window can exist at all.
kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain by getting {
            dependencies {
                // The rendering path, exactly the one a client takes: the registry and its dispatch,
                // the standard renderers, the preview seam that turns a body into a frame.
                //
                // `api` where the type appears in this module's public signatures — KompotRegistry in
                // the configuration, KompotTheme in the default frame's parameters. A consumer who
                // cannot name a parameter cannot call the function, and the build stays green either
                // way: only somebody compiling against the artefact finds out (#70).
                api(projects.kompotClient)
                api(projects.kompotTheme)
                // The overlay that turns a served theme into a design system. The default frame is the
                // only caller — a consumer with a frame of its own never needs it — so implementation.
                implementation(projects.kompotThemeClient)
                // api, not implementation: KompotPageLoader is a parameter of KompotStudioConfig.
                api(projects.kompotStandard)
                implementation(projects.kompotPreview)
                // The standard field set, for the one thing the studio does with values: filling a
                // form with plausible ones. A deployment with its own value types still gets the empty
                // and errors pictures, which need none.
                implementation(projects.formStandard)
                implementation(projects.formCore)
                // The graph an HTTP source reads its screen list from. Not `api`: a consumer names a
                // ScreenSource.Http with a path, never a NavigationGraph.
                implementation(projects.kompotNavigation)
                // The design system the renderers resolve tokens through. A real one rather than a
                // two-line stub: the question this spike asks about Material is only meaningful with
                // the Material design system in the composition.
                implementation(projects.kompotDsMaterialCompose)
                // The vocabulary: the closed list of types and the validator for it. JVM-only, which
                // is the second reason this module has one target.
                implementation(projects.kompotSpec)

                implementation(libs.compose.material3)
                // Json and JsonObject are both in the configuration's signature.
                api(libs.kotlinx.serialization.json)
                // StateFlow is in the signature of a source session, which a consumer can implement.
                api(libs.kotlinx.coroutines.core)
                // The frame is a @Composable typealias, so a consumer writing one needs the runtime.
                api(libs.compose.runtime)
                // One call: JBR.isAvailable(), the condition DecoratedWindow refuses on.
                implementation(libs.jbr.api)

                // Jewel, the shell, is declared in the `dependencies` block at the bottom rather
                // than here — see the comment there.
            }
        }

        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
                // The screenshot tester, for one question only: whether a frame taken headlessly out
                // of the same composition is the frame the window shows. It carries JUnit and
                // currentOs as api — test scope, never main.
                implementation(libs.viddik.testing.core)
                // The annotation module, so the test can stand up a registry of the real shape at the
                // name the generator writes — reflection asserted against a class, not a guess.
                implementation(libs.viddik.annotations)
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.test)
                // A real click on a real button: the action log's whole claim is that a tap reaches
                // the handler, and nothing short of pressing one proves it.
                implementation(libs.ui.test)
            }
        }
    }
}

// The shell, and it is down here for a mechanical reason worth writing down: a source set's
// `dependencies` block takes a catalog entry OR a configuration lambda, never both —
// `implementation(libs.x) { exclude(...) }` there fails to compile with a message about String. The
// module-level handler takes both, and names the configuration the source set generated.
//
// Material 2 is excluded on Jewel's own instruction: Jewel themes itself through Material 2's tokens,
// the toolkit's renderers read material3, and two generations of Material in one classpath is exactly
// the kind of thing that resolves cleanly and then fails inside a renderer.
//
// The int-ui-decorated-window coordinate is what Jewel's documentation names; in 0.40 it is an empty
// shim depending on jewel-decorated-window, which depends on jewel-int-ui-standalone. Both are named
// because this module uses both things — the IntUi theme and the decorated window — and a dependency
// that arrives only transitively is a dependency nobody declared.
dependencies {
    "desktopMainImplementation"(libs.jewel.int.ui.standalone) {
        exclude(group = "org.jetbrains.compose.material", module = "material")
    }
    "desktopMainImplementation"(libs.jewel.int.ui.decorated.window) {
        exclude(group = "org.jetbrains.compose.material", module = "material")
    }
}

compose.desktop {
    application {
        mainClass = "io.github.youndie.kompot.studio.StudioDemoKt"
    }
}

// THE RUNTIME, and both halves of it were measured rather than chosen.
//
// A JetBrains Runtime, because two things this module wants exist only there: Jewel's DecoratedWindow,
// whose first statement is `if (!JBR.isAvailable()) error(...)` — it does not degrade to a plain
// window, the research's §5.5 had that wrong — and Compose Hot Reload, which instruments a running
// JVM.
//
// 25 and not 21, and that number is Jewel's rather than ours: 0.40 is cut from IntelliJ platform 262
// and ships CLASS FILE 69, so a 21 runtime dies at class loading with UnsupportedClassVersionError on
// JewelTheme — after the window's own code compiled, published and passed its tests. Nothing about
// the ARTEFACT moves: this module's bytecode is Java 17 like every other module's (`sborka.jvmFloor`),
// and the audit in CI checks that. Only the JVM that runs it is newer.
//
// A toolchain rather than "have a JBR installed": the launcher is resolved when the task RUNS, so
// `./gradlew build` never asks for it, and a machine without one provisions it instead of silently
// running the fallback.
val jetBrainsRuntime =
    javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(25)
        vendor = JvmVendorSpec.JETBRAINS
    }

// configureEach with a name guard, and not `tasks.named("run")`: the Compose plugin registers `run`
// from inside its own afterEvaluate, so naming it here fails at configuration time with "Task with
// name 'run' not found" — the task is real and simply does not exist yet when this line is read.
// `executable` and not `javaLauncher`, which is the property one reaches for first: the Compose
// plugin already sets `executable` on its run task, and Gradle then refuses the task outright with
// "Toolchain from `executable` property does not match toolchain from `javaLauncher` property".
// Overwriting the one the plugin uses is the way in.
//
// configureEach with a name guard, and not `tasks.named("run")`: the Compose plugin registers `run`
// from inside its own afterEvaluate, so naming it here fails at configuration time with "Task with
// name 'run' not found" — the task is real and simply does not exist yet when this line is read. The
// guard also keeps `.get()` — and with it the download — out of every build that does not run.
// skiko's host-native half, on the RUN classpath and on no other. It cannot go in `desktopMain`: this
// module is published now, and `compose.desktop.currentOs` resolves to the host it was built on — it
// would pin a macOS classifier in the POM every consumer reads. A configuration of its own says
// "needed to run THIS, here" without saying anything about the artefact.
val studioRuntime: Configuration by configurations.creating

dependencies {
    studioRuntime(compose.desktop.currentOs)
    // The screenshot tester, on the RUN classpath only — which is exactly how a consumer adds it
    // (`runtimeOnly`), and why the studio reaches it by reflection rather than by naming it: it
    // carries currentOs and JUnit as `api`, and a published tool must carry neither.
    studioRuntime(libs.viddik.testing.core)
}

// afterEvaluate, and it is the only place that works. The Compose plugin builds its `run` task inside
// its OWN afterEvaluate — mainClass, classpath and executable are all set there — so anything said
// about the task earlier is overwritten without a word: `tasks.named("run")` at configuration time
// fails outright ("Task with name 'run' not found"), and a `configureEach` block that does find it
// runs BEFORE the plugin and loses. Both were tried; the second is the one that looks like it worked,
// because the build stays green and the application starts on the wrong JVM with skiko missing.
//
// Callback order is registration order, and the plugin registered its when it was applied — above, in
// the `plugins` block — so this one runs after.
afterEvaluate {
    tasks.named<JavaExec>("run") {
        setExecutable(jetBrainsRuntime.get().executablePath.asFile.absolutePath)
        classpath += studioRuntime

        // Two directories the demo can be pointed at, forwarded from the command line: -D on a Gradle
        // invocation reaches the GRADLE jvm, not this one, which is the sort of thing that reads as
        // "the flag does nothing".
        listOf("kompot.studio.snapshots", "kompot.studio.recordings").forEach { key ->
            (project.findProperty(key) as? String)?.let { systemProperty(key, it) }
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()

    // The SAME runtime the application runs on, and that identity is the point: a test that asserts
    // the studio's runtime while running on a different one asserts nothing. It also clears the floor
    // :kompot-ds-material-compose raises for the same suite — viddik ships class file 65 while
    // everything here compiles on 17.
    javaLauncher = jetBrainsRuntime
}
