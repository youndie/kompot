plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("kompot.publishing")
}

dependencies {
    // Only JSON. The corpus is data and the adapter is the implementer's: nothing here decodes a form
    // into Kotlin types, because the moment it did, the contract would stop being one a client on
    // another language could satisfy.
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(projects.formCore)
    testImplementation(projects.formStandard)
    testImplementation(libs.kotlinx.coroutines.test)
}
