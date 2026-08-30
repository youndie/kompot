plugins {
    kotlin("multiplatform")
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    kotlin("plugin.serialization")
    id("ru.workinprogress.sborka.kmp")
    id("ru.workinprogress.sborka.publish")
}


kotlin {
    jvm()
    androidLibrary {
        namespace = "io.github.youndie.wizard.core"
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
