plugins {
    kotlin("multiplatform")
    id("kompot.publishing")
}



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
