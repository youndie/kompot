plugins {
    kotlin("multiplatform")
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    kotlin("plugin.serialization")
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ksp)
    id("ru.workinprogress.sborka.kmp")
    id("ru.workinprogress.sborka.publish")
}

kotlin {
    jvm("desktop")
    // Two Apple targets rather than the three the protocol modules carry: compose.runtime published
    // its last iosX64 artefact at 1.11.0-alpha01, so an Intel simulator is not reachable for anything
    // that depends on Compose (see the Targets section of the readme).
    iosArm64()
    iosSimulatorArm64()
    androidLibrary {
        namespace = "io.github.youndie.kompot.wizard.client"
        compileSdk = 36
        minSdk = 24
    }
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            api(projects.kompotCore)
            // kompot-client, а не наоборот — тот же принцип, что у kompot-forms-client/
            // kompot-banking-client/kompot-images-client-coil.
            api(projects.kompotClient)
            api(projects.kompotWizard)
            // `api`, not `implementation`: the registry the processor generates is public and typed by
            // this module's annotations — `generated…Docs` is a map of KompotComponentDoc — so a consumer
            // compiling against this artefact has to be able to name them. A consumer check on the
            // published artefacts failed on exactly that while this build stayed green.
            api(projects.kompotRegistryAnnotations)
            api(projects.formCore)
            implementation(libs.kotlinx.coroutines.core)

            api(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            api(libs.compose.ui)
        }

        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(compose.desktop.currentOs)
                implementation(libs.ui.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(projects.kompotStandard)
                implementation(projects.formStandard)
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
    arg("kompotModuleTag", "WizardClient")
}

kotlin.sourceSets.commonMain {
    kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
}

// Everything that READS the generated directory has to wait for it, and that is not only the
// compilers: the per-target sources jars package commonMain too, and Gradle refuses an undeclared
// dependency between the two. Matching on the consumers rather than listing task names, so a target
// added later is covered without anybody remembering to.
tasks.matching {
    (it.name.startsWith("compile") || it.name.lowercase().endsWith("sourcesjar")) &&
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
