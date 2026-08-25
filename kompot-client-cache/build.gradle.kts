plugins {
    kotlin("multiplatform")
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    kotlin("plugin.serialization")
    id("kompot.publishing")
}



kotlin {
    jvm()
    androidLibrary {
        namespace = "io.github.youndie.kompot.client.cache"
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
                implementation(libs.kotlinx.serialization.json)
                // api and not implementation: the public API of this module hands the type out, so a consumer
                // that cannot name it cannot call the function. The build, the tests and the publish stay green
                // either way — only somebody compiling against the artefact finds out (see #70).
                api(libs.kotlinx.coroutines.core)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}
