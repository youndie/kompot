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
                // TextComponent.styleKey(): unwrapping a TypographyToken (see KompotTokenBridge).
                api(projects.kompotStandard)
                api(projects.formCore)
                // KompotFormResponse and FormPatchRequest travel through the JSON bridge.
                api(projects.kompotForms)
                // An image's tint is a ColorToken, erased at the ObjC export boundary.
                api(projects.kompotImages)
                // UpdateComponentMessage needs the same non-generic escape hatch as a component does.
                api(projects.kompotRealtime)
                // The wizard actions and WizardResumeRequest travel through the bridge as well.
                api(projects.kompotWizard)
                api(projects.wizardCore)
                api(libs.kotlinx.serialization.json)
                api(libs.kotlinx.coroutines.core)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}
