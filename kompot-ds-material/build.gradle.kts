plugins {
    kotlin("multiplatform")
    id("kompot.publishing")
}


// Pure Kotlin, no UI toolkit at all: these are the string keys a Material3 client resolves, and a
// headless server authors trees with the very same constants. That is the point of a token being an
// open string — see SPEC.md §6.
kotlin {
    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    wasmJs { browser() }

    sourceSets {
        commonMain {
            dependencies {
                api(projects.kompotCore)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
