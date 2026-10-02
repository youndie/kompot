plugins {
    kotlin("multiplatform")
    alias(wip.plugins.androidKotlinMultiplatformLibrary)
    id("io.github.youndie.sborka.kmp")
    id("io.github.youndie.sborka.lint")
    alias(libs.plugins.dokka)
    id("io.github.youndie.sborka.publish")
}

kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation { }

    jvm()
    androidLibrary {
        namespace = "io.github.youndie.kompot.registry.annotations"
        compileSdk = 37
        minSdk = 24
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    wasmJs { browser() }
}
