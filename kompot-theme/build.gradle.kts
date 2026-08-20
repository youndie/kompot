plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("kompot.publishing")
}

group = "io.github.youndie"


kotlin {
    jvm()

    sourceSets {
        commonMain {
            dependencies {
                // Only for typed ColorToken/TypographyToken in the DSL and the accessors; on the wire
                // the keys are plain strings anyway, being JSON object keys. See KompotTheme.kt.
                api(projects.kompotCore)
                api(libs.kotlinx.serialization.json)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(projects.kompotCore)
            }
        }
    }
}
