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
                // Only the bus contract and local delivery — no HTTP server, no Redis, no SSE. A concrete
                // transport between instances is plugged in by a separate module (:kompot-realtime-redis).
                api(libs.kotlinx.coroutines.core)
                // For the typed broadcast(topic, json, UpdateComponentMessage) wrapper: the bus itself
                // carries an opaque string, but an application would rather hand over a protocol message
                // than serialise it at every call site.
                api(projects.kompotRealtime)
                api(libs.kotlinx.serialization.json)
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
