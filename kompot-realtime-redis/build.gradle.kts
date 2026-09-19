plugins {
    kotlin("jvm")
    id("io.github.youndie.sborka.jvm")
    alias(libs.plugins.dokka)
    id("io.github.youndie.sborka.publish")
}


// A plain kotlin("jvm") rather than multiplatform + jvm(): Lettuce is a JVM library, and there is
// nothing to gain from wrapping a single JVM target in multiplatform scaffolding.
dependencies {
    api(projects.kompotRealtimeServer)
    // RedisClient is a parameter of the public constructor, so it belongs to this module's own API:
    // a consumer that builds the bus from a client it already has cannot compile without it.
    api(libs.lettuce)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation { }
}
