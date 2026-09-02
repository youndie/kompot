plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    id("ru.workinprogress.sborka.kmp")
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
                implementation(projects.kompotClient)
                implementation(projects.kompotStandard)
                implementation(projects.kompotPreview)
                // The design system the renderers resolve tokens through. A real one rather than a
                // two-line stub: the question this spike asks about Material is only meaningful with
                // the Material design system in the composition.
                implementation(projects.kompotDsMaterialCompose)
                // The vocabulary: the closed list of types and the validator for it. JVM-only, which
                // is the second reason this module has one target.
                implementation(projects.kompotSpec)

                implementation(libs.compose.material3)
                implementation(libs.kotlinx.serialization.json)
                // One call: JBR.isAvailable(), the condition DecoratedWindow refuses on.
                implementation(libs.jbr.api)

                // Jewel, the shell, is declared in the `dependencies` block at the bottom rather
                // than here — see the comment there.

                // The host-native half of skiko. Legal here and nowhere else in this repository: this
                // module is not published, so nothing pins a host in anybody's POM.
                implementation(compose.desktop.currentOs)
            }
        }

        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
                // The screenshot tester, for one question only: whether a frame taken headlessly out
                // of the same composition is the frame the window shows. It carries JUnit and
                // currentOs as api — test scope, never main.
                implementation(libs.viddik.testing.core)
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
        mainClass = "io.github.youndie.kompot.studio.SpikeKt"
    }
}

// THE RUNTIME, and it is a JetBrains Runtime on purpose. Two things this module wants exist only
// there: Jewel's DecoratedWindow, whose first statement is `if (!JBR.isAvailable()) error(...)` —
// it does not degrade to a plain window, the research's §5.5 had that wrong — and Compose Hot Reload,
// which instruments a running JVM. A toolchain rather than "have a JBR installed": the launcher is
// resolved when this task RUNS, so `./gradlew build` never asks for it, and a machine without one
// provisions it instead of silently running the fallback.
val jetBrainsRuntime =
    javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(21)
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
tasks.withType<JavaExec>().configureEach {
    if (name == "run") setExecutable(jetBrainsRuntime.get().executablePath.asFile.absolutePath)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()

    // The SAME runtime the application runs on, and 21 for the same reason :kompot-ds-material-compose
    // asks for it: viddik ships class file 65 while everything here compiles on 17. Running the tests
    // on the JetBrains Runtime as well is what makes JetBrainsRuntimeTest mean anything — a test that
    // asserts the studio's runtime while running on a different one asserts nothing.
    javaLauncher = jetBrainsRuntime
}
