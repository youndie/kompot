plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("kompot.publishing")
}

group = "io.github.youndie"

dependencies {
    // api rather than implementation: a consumer configures the runner with its own schemas and its
    // own OpenAPI document, so the spec types are part of this module's own surface.
    api(projects.kompotSpec)
    api(libs.ktor.clientCore)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.clientCio)

    testImplementation(kotlin("test"))
}
