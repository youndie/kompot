plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    alias(libs.plugins.ksp)
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
                implementation(projects.kompotRegistryAnnotations)
                // WizardStepAction and WizardResumeRequest carry a form's typed FieldValue instances and a
                // WizardTransition over the same wire as every other Kompot action, so this module needs
                // those types rather than a flat Map<String, String>.
                api(projects.formCore)
                api(projects.wizardCore)
                api(libs.kotlinx.serialization.json)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(projects.kompotStandard)
            }
        }
    }
}

dependencies {
    add("kspJvm", project(":kompot-registry-processor"))
    add("kspIosX64", project(":kompot-registry-processor"))
    add("kspIosArm64", project(":kompot-registry-processor"))
    add("kspIosSimulatorArm64", project(":kompot-registry-processor"))
}

ksp {
    arg("kompotModuleTag", "Wizard")
}

kotlin.sourceSets.getByName("jvmMain") {
    kotlin.srcDir("build/generated/ksp/jvm/jvmMain/kotlin")
}

kotlin.sourceSets.getByName("iosX64Main") {
    kotlin.srcDir("build/generated/ksp/iosX64/iosX64Main/kotlin")
}

kotlin.sourceSets.getByName("iosArm64Main") {
    kotlin.srcDir("build/generated/ksp/iosArm64/iosArm64Main/kotlin")
}

kotlin.sourceSets.getByName("iosSimulatorArm64Main") {
    kotlin.srcDir("build/generated/ksp/iosSimulatorArm64/iosSimulatorArm64Main/kotlin")
}
