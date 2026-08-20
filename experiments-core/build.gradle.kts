plugins {
    kotlin("multiplatform")
    id("kompot.publishing")
}

group = "io.github.youndie"


kotlin {
    jvm()

    sourceSets {
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
