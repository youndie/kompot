package io.github.youndie.kompot.studio.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

// ONE COMMAND, AND THE CONFIGURATION COMES FROM THE BUILD IT RUNS IN.
//
// The studio is a library a consumer runs, for the reason the research settled on: the renderers it
// must draw with are the consumer's, and loading them from another process would mean two Compose
// runtimes. That leaves the question of how a TEAM opens it — a `main` and a run configuration is an
// answer for whoever wrote them.
//
// One thing the plugin cannot do for the build it runs in: declare a repository. The studio's chrome
// is Jewel, and the SVG bundle behind Jewel's icons is published only in the IntelliJ repository —
// a consumer adds it, filtered to its one group:
//
//     maven("https://www.jetbrains.com/intellij-repository/releases") {
//         content { includeGroup("com.jetbrains.intellij.platform") }
//     }
//
// Without it the studio still opens, and every chevron in it is a magenta square.
public open class KompotStudioExtension {
    // Which Kotlin target's classpath the studio runs on. `jvm` covers both the common name and a
    // plain kotlin("jvm") project; a multiplatform build that calls its desktop target something else
    // says so here.
    public var target: String = "jvm"

    // `main` or `test`. NOT a detail: a consumer's brand frame, its recorded responses and its goldens
    // are usually test sources — the studio pilot on the first real consumer ran from `test` for
    // exactly that reason, and a plugin that only offered `main` would have been unusable there.
    public var compilation: String = "main"
}

public class KompotStudioPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val extension = target.extensions.create<KompotStudioExtension>("kompotStudio")

        val studioTools = target.configurations.dependencyScope("kompotStudioTools")

        // The plugin's OWN classpath additions, and they are the plugin's on purpose. skiko's
        // host-native half resolves to the machine it was built on, and viddik carries a test
        // framework: both belong to running the studio and neither belongs in the POM of a module the
        // consumer publishes.
        val studioRuntime =
            // TWO configurations, and Gradle 9 requires both: dependencies are DECLARED on a
            // dependency-scope one and RESOLVED through a resolvable one that extends it. Declaring on
            // a resolvable configuration fails outright — "Dependencies can not be declared against
            // the … configuration" — which is the role model saying the two jobs are different.
            target.configurations.resolvable("kompotStudioRuntime") { it.extendsFrom(studioTools.get()) }.get()

        // skiko's host-native half, resolved for the machine the task runs on. Only viddik is NOT
        // added beside it, though the plan asked for both: it lives in a repository a consumer's build
        // need not have declared, so naming it would turn a missing repository into a task that cannot
        // even configure. The capture is reached reflectively and absent is a supported state, so a
        // build that wants goldens adds the one line itself.
        target.dependencies.add(
            studioTools.name,
            "org.jetbrains.compose.desktop:desktop-jvm-${hostSuffix()}:$COMPOSE_VERSION",
        )

        target.tasks.register<JavaExec>("kompotStudio") {
            group = "application"
            description = "Open the kompot studio on this build's renderers, brands and recordings"
            mainClass.set(LAUNCHER)

            // Resolved lazily: the Kotlin plugin has not necessarily declared its targets when this
            // block runs, and asking early gets an empty classpath and a task that starts and finds
            // nothing.
            // The consumer's classpath AND the plugin's own additions. Written as a lambda so the
            // Kotlin plugin has declared its targets by the time it is read: asking early gets an empty
            // classpath and a task that starts and finds nothing.
            classpath = target.files({ consumerClasspath(target, extension) }, studioRuntime)

            // Jewel ships class file 69 and its decorated window refuses to open on anything but a
            // JetBrains Runtime — both measured in the toolkit rather than assumed. Asking for the
            // vendor by toolchain means a machine without one provisions it instead of silently
            // running a window with no decorations.
            javaLauncher.set(
                target.extensions.getByType<JavaToolchainService>().launcherFor { spec: JavaToolchainSpec ->
                    spec.languageVersion.set(JavaLanguageVersion.of(RUNTIME_VERSION))
                    spec.vendor.set(JvmVendorSpec.JETBRAINS)
                },
            )
        }
    }

    // A multiplatform target's compilation where there is one, and a plain JVM source set otherwise.
    // Both, because a consumer's client module is usually multiplatform and a server-side preview is
    // not.
    private fun consumerClasspath(
        project: Project,
        extension: KompotStudioExtension,
    ): FileCollection {
        val multiplatform = project.extensions.findByType(KotlinMultiplatformExtension::class.java)

        if (multiplatform != null) {
            val kotlinTarget =
                multiplatform.targets.findByName(extension.target)
                    ?: error(
                        "kompotStudio: no Kotlin target \"${extension.target}\" in ${project.path}. " +
                            "Set kompotStudio { target = \"…\" } to the one whose classpath the studio should run on.",
                    )
            val compilation =
                kotlinTarget.compilations.findByName(extension.compilation)
                    ?: error("kompotStudio: no \"${extension.compilation}\" compilation on ${kotlinTarget.name}")

            return project.files(
                compilation.output.allOutputs,
                compilation.runtimeDependencyFiles,
                compilation.compileDependencyFiles,
            )
        }

        val sourceSets =
            project.extensions.findByType(SourceSetContainer::class.java)
                ?: error("kompotStudio: ${project.path} is neither a Kotlin Multiplatform nor a JVM project")
        val sourceSet =
            sourceSets.findByName(extension.compilation)
                ?: error("kompotStudio: no \"${extension.compilation}\" source set in ${project.path}")

        return project.files(sourceSet.output, sourceSet.runtimeClasspath)
    }

    private fun hostSuffix(): String {
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()
        val architecture = if (arch == "aarch64" || arch == "arm64") "arm64" else "x64"

        return when {
            os.contains("mac") -> "macos-$architecture"
            os.contains("windows") -> "windows-$architecture"
            else -> "linux-$architecture"
        }
    }

    private companion object {
        const val LAUNCHER = "io.github.youndie.kompot.studio.KompotStudioLauncher"

        // Jewel's requirement rather than ours; see the comment on the launcher.
        const val RUNTIME_VERSION = 25

        // The Compose line this toolkit is on. A constant rather than a lookup: the plugin is published
        // together with the studio and the two move in one commit.
        const val COMPOSE_VERSION = "1.11.1"
    }
}
