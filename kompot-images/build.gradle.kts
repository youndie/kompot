plugins {
    kotlin("multiplatform")
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    kotlin("plugin.serialization")
    alias(libs.plugins.ksp)
    id("io.github.youndie.sborka.kmp")
    alias(libs.plugins.dokka)
    id("io.github.youndie.sborka.publish")
}


kotlin {
    jvm()
    androidLibrary {
        namespace = "io.github.youndie.kompot.images"
        compileSdk = 36
        minSdk = 24
        // The common tests run on this target too. They pass on jvm already and the modules are
        // common code with no expect/actual, so this is not about a second answer — it is about the
        // target that has none: without it the plugin skips them with a warning, and a suite nobody
        // runs on a platform is the same silence as a suite that does not exist.
        withHostTest {}
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    wasmJs { browser() }

    sourceSets {
        commonMain {
            dependencies {
                api(projects.kompotCore)
                // `api`, not `implementation`: the registry the processor generates is public and typed by
                // this module's annotations — `generated…Docs` is a map of KompotComponentDoc — so a consumer
                // compiling against this artefact has to be able to name them. A consumer check on the
                // published artefacts failed on exactly that while this build stayed green.
                api(projects.kompotRegistryAnnotations)
                api(libs.kotlinx.serialization.json)
            }
        }
        commonTest {
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
    arg("kompotModuleTag", "Images")
}

// Where the generated sources go, who is made to wait for them, and which ksp tasks are switched
// off because they have no processor: `io.github.youndie.sborka.kmp`, keyed off the dependency
// above. Three paragraphs that stood here byte-identical in seven modules, and one fix to a race
// with dokka had to visit all seven (B-33, B-35).
