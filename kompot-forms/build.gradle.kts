plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    alias(libs.plugins.ksp)
    id("kompot.publishing")
}

group = "io.github.youndie"


kotlin {
    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            dependencies {
                api(projects.kompotCore)
                // Needed for KompotFormResponse: the form schema and the render tree as one DTO.
                api(projects.formCore)
                implementation(projects.kompotRegistryAnnotations)
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

dependencies {
    add("kspJvm", project(":kompot-registry-processor"))
    add("kspIosX64", project(":kompot-registry-processor"))
    add("kspIosArm64", project(":kompot-registry-processor"))
    add("kspIosSimulatorArm64", project(":kompot-registry-processor"))
}

ksp {
    arg("kompotModuleTag", "Forms")
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
