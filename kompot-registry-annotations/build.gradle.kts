plugins {
    kotlin("multiplatform")
    id("kompot.publishing")
}

group = "io.github.youndie"

kotlin {
    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()
}
