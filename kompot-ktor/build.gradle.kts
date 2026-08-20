plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("kompot.publishing")
}

group = "io.github.youndie"


kotlin {
    jvm()

    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.kompotCore)
                // Only the header format (ExperimentHeaderCodec). This module neither assigns
                // variants nor decides what to show; it transports a decision the application has
                // already made. See ExperimentHeaders.kt.
                implementation(projects.experimentsCore)
                implementation(libs.ktor.serverCore)
                implementation(libs.ktor.serverContentNegotiation)
                implementation(libs.ktor.serializationJson)
                implementation(libs.ktor.serverStatusPages)

                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.ktor.serverTestHost)
                implementation(libs.ktor.serverContentNegotiation)
                implementation(libs.ktor.serializationJson)
            }
        }
    }
}
