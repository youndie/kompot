plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("kompot.publishing")
}


dependencies {
    // api rather than implementation: a consumer configures the runner with its own schemas and its
    // own OpenAPI document, so the spec types are part of this module's own surface.
    api(projects.kompotSpec)
    // For ScreenRouteKind: the vocabulary of route kinds belongs to the protocol module that defines
    // it, and the kit reads a graph rather than inventing the names a second time.
    implementation(projects.kompotNavigation)
    api(libs.ktor.clientCore)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.clientCio)

    testImplementation(kotlin("test"))
}
