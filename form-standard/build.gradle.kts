plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("kompot.publishing")
}


kotlin {
    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    wasmJs { browser() }

    sourceSets {
        commonMain {
            dependencies {
                api(projects.formCore)
                api(libs.kotlinx.serialization.json)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
