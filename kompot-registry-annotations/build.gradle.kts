plugins {
    kotlin("multiplatform")
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    id("ru.workinprogress.sborka.kmp")
    id("ru.workinprogress.sborka.publish")
}


kotlin {
    jvm()
    androidLibrary {
        namespace = "io.github.youndie.kompot.registry.annotations"
        compileSdk = 36
        minSdk = 24
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    wasmJs { browser() }
}
