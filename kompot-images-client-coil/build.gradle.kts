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
        namespace = "io.github.youndie.kompot.images.client.coil"
        compileSdk = 36
        minSdk = 24
    }
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            api(projects.kompotCore)
            // kompot-client, а не наоборот — этот модуль потребляет KompotComponentRenderer/
            // LocalKompotDesignSystem/LocalKompotRegistry, сам kompot-client про Coil ничего не знает
            // (см. комментарий в kompot-client/build.gradle.kts про синтетический KompotImageComponent).
            api(projects.kompotClient)
            api(projects.kompotImages)
            // `api`, not `implementation`: the registry the processor generates is public and typed by
            // this module's annotations — `generated…Docs` is a map of KompotComponentDoc — so a consumer
            // compiling against this artefact has to be able to name them. A consumer check on the
            // published artefacts failed on exactly that while this build stayed green.
            api(projects.kompotRegistryAnnotations)
            // KompotComponentRenderer.Render принимает FormController в сигнатуре — нужен на
            // компайл-класспасе любому модулю, реализующему интерфейс (kompot-client сам
            // подключает form-core как implementation, не api, поэтому это не транзитивно).
            api(projects.formCore)

            api(libs.compose.runtime)
            implementation(libs.compose.foundation)
            api(libs.compose.ui)

            // api and not implementation: the public API of this module hands the type out, so a consumer
            // that cannot name it cannot call the function. The build, the tests and the publish stay green
            // either way — only somebody compiling against the artefact finds out (see #70).
            api(libs.coil3.compose)
            implementation(libs.coil3.network.ktor3)
            implementation(libs.coil3.svg)
        }

        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
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
    arg("kompotModuleTag", "ImagesClient")
}

// Where the generated sources go, who is made to wait for them, and which ksp tasks are switched
// off because they have no processor: `io.github.youndie.sborka.kmp`, keyed off the dependency
// above. Three paragraphs that stood here byte-identical in seven modules, and one fix to a race
// with dokka had to visit all seven (B-33, B-35).
