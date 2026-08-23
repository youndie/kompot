plugins {
    kotlin("multiplatform")
    id("kompot.publishing")
}


kotlin {
    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    wasmJs { browser() }
}
