plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("ru.workinprogress.sborka.kmp")
    id("ru.workinprogress.sborka.publish")
}


// JVM only, as it was: this DSL builds a form on the server, and nothing in the iOS framework exports
// it. Widening the target set is a decision for whoever needs it, not a side effect of a move.
kotlin {
    jvm()

    sourceSets {
        commonMain {
            dependencies {
                api(projects.kompotCore)
                api(projects.kompotStandard)
                api(projects.kompotForms)
                api(projects.formCore)
                api(projects.formStandard)
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
