plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    alias(libs.plugins.ksp)
    id("kompot.publishing")
}


kotlin {
    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    wasmJs { browser() }

    sourceSets {
        commonMain {
            dependencies {
                api(projects.kompotCore)
                implementation(projects.kompotRegistryAnnotations)
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
