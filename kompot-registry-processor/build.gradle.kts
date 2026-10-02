plugins {
    kotlin("jvm")
    id("io.github.youndie.sborka.jvm")
    id("io.github.youndie.sborka.lint")
    alias(libs.plugins.dokka)
    id("io.github.youndie.sborka.publish")
}


dependencies {
    // The KSP API at the version of the KSP plugin, which comes from `wip`. Spelled here against
    // `wip.versions` rather than as a catalog entry: the shared catalog carries what going out of step
    // BETWEEN repositories would break, and the processor API has this one reader.
    implementation("com.google.devtools.ksp:symbol-processing-api:${wip.versions.ksp.get()}")
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
}

kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation { }
}
