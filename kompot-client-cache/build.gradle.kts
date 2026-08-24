plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
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
