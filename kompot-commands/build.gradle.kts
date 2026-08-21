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

    sourceSets {
        commonMain {
            dependencies {
                api(projects.kompotCore)
                // form-core, not kompot-forms: this module needs the VALUE vocabulary, not the form
                // envelope. form-core depends on nothing of kompot's, so taking it does not drag a
                // form's UI, its schema or its patch protocol along.
                api(projects.formCore)
                api(libs.kotlinx.serialization.json)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(projects.formStandard)
            }
        }
    }
}
