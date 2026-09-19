plugins {
    kotlin("multiplatform")
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    kotlin("plugin.serialization")
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ksp)
    id("io.github.youndie.sborka.kmp")
    alias(libs.plugins.dokka)
    id("io.github.youndie.sborka.publish")
}

kotlin {
    jvm("desktop")
    // Two Apple targets rather than the three the protocol modules carry: compose.runtime published
    // its last iosX64 artefact at 1.11.0-alpha01, so an Intel simulator is not reachable for anything
    // that depends on Compose (see the Targets section of the readme).
    iosArm64()
    iosSimulatorArm64()
    androidLibrary {
        namespace = "io.github.youndie.kompot.forms.client"
        compileSdk = 36
        minSdk = 24
    }
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            api(projects.kompotCore)
            // kompot-client, а не наоборот — этот модуль потребляет KompotComponentRenderer/
            // LocalKompotDesignSystem (см. комментарий в kompot-images-client-coil/build.gradle.kts
            // про тот же принцип для картинок).
            api(projects.kompotClient)
            api(projects.kompotForms)
            // `api`, not `implementation`: the registry the processor generates is public and typed by
            // this module's annotations — `generated…Docs` is a map of KompotComponentDoc — so a consumer
            // compiling against this artefact has to be able to name them. A consumer check on the
            // published artefacts failed on exactly that while this build stayed green.
            api(projects.kompotRegistryAnnotations)
            // KompotComponentRenderer.Render принимает FormController в сигнатуре, и рендереры полей
            // сами читают/пишут состояние формы через FormController.collectFieldState и т.п.
            api(projects.formCore)
            // NOT api, though the same reader flagged four of its types: every mention is inside a
            // synthetic class inlining left behind — AmountInputRenderer$Render$$inlined$collectFieldState$1
            // and lambdas like it. A consumer calling these renderers names none of them.
            implementation(projects.formStandard)

            api(libs.compose.runtime)
            implementation(libs.compose.foundation)
            api(libs.compose.material3)
            api(libs.compose.ui)
        }

        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
                // Compose UI-тесты рендерят реальное (офскрин) дерево через Skiko — нужен
                // рантайм текущей ОС, а не только API тестового фреймворка.
                implementation(compose.desktop.currentOs)
                implementation(libs.ui.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

dependencies {
    // kspCommonMainMetadata, not one entry per target: the annotated types are all in
    // commonMain, and per-target output lands in a PLATFORM source set — where the metadata a
    // consumer's commonMain compiles against can never see it. A single-target consumer never
    // noticed; adding a second target to one turned it into an unresolved reference.
    add("kspCommonMainMetadata", project(":kompot-registry-processor"))
}

ksp {
    arg("kompotModuleTag", "FormsClient")
}

// Where the generated sources land. The producing task is deliberately NOT attached to this directory
// with `builtBy`: KSP reads the very source set it writes into, so making the source set depend on the
// producer makes the producer depend on itself — Gradle answers with a circular dependency between
// kspCommonMainKotlinMetadata and kspCommonMainKotlinMetadata. That is why the consumers below are
// listed by name rather than served by the collection.
kotlin.sourceSets.commonMain {
    kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
}

// For the consumers that take the PATHS out of the source set instead of the collection: those lose
// the dependency the collection carries, and there is no way to ask Gradle which ones do.
//
// This list used to be the whole mechanism, and it named two kinds of consumer. Dokka was a third —
// it reads the same directory, matched neither pattern, and therefore ran CONCURRENTLY with KSP,
// which wipes its output directory before regenerating it. The symptom was a publish step failing
// with FileNotFoundException on a file that does exist, once in every few runs, on branches that had
// not touched this module (B-33). The list is incomplete by nature; the collection above is what
// covers whoever is not on it.
tasks.matching {
    (it.name.startsWith("compile") || it.name.startsWith("dokka") || it.name.lowercase().endsWith("sourcesjar")) &&
        it.name != "kspCommonMainKotlinMetadata"
}.configureEach {
    dependsOn("kspCommonMainKotlinMetadata")
}

// The per-target ksp tasks are registered by the plugin for every target and now have no processor of
// their own, but they still read the metadata output as a source directory — which Gradle reports as
// an undeclared dependency between tasks. Generation happens once, so they are switched off.
//
// A disabled ksp task is the classic way to get a green and EMPTY build, so the exit code proves
// nothing here. What does: the serializer tests in commonTest round-trip real components THROUGH the
// generated module, on every target including the browser — an empty registration fails them.
tasks.matching { it.name.startsWith("ksp") && it.name != "kspCommonMainKotlinMetadata" }.configureEach {
    enabled = false
}
