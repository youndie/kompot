plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("kompot.publishing")
}



kotlin {
    jvm()

    sourceSets {
        commonMain {
            dependencies {
                // Every function here is an extension on ApplicationCall taking or returning a
                // KompotComponent, so both are part of this module's own API rather than an internal detail.
                api(projects.kompotCore)
                // Only the header format (ExperimentHeaderCodec). This module neither assigns
                // variants nor decides what to show; it transports a decision the application has
                // already made. See ExperimentHeaders.kt.
                implementation(projects.experimentsCore)
                api(libs.ktor.serverCore)
                implementation(libs.ktor.serverContentNegotiation)
                implementation(libs.ktor.serializationJson)
                implementation(libs.ktor.serverStatusPages)

                api(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
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
