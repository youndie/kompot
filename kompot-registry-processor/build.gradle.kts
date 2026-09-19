plugins {
    kotlin("jvm")
    id("io.github.youndie.sborka.jvm")
    alias(libs.plugins.dokka)
    id("io.github.youndie.sborka.publish")
}


dependencies {
    implementation(libs.ksp.symbol.processing.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
}

kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation { }
}
