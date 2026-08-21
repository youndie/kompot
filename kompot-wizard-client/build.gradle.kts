plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ksp)
    id("kompot.publishing")
}

kotlin {
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(projects.kompotCore)
            // kompot-client, а не наоборот — тот же принцип, что у kompot-forms-client/
            // kompot-banking-client/kompot-images-client-coil.
            implementation(projects.kompotClient)
            implementation(projects.kompotWizard)
            implementation(projects.kompotRegistryAnnotations)
            implementation(projects.formCore)
            implementation(libs.kotlinx.coroutines.core)

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
        }

        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(compose.desktop.currentOs)
                implementation(libs.ui.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(projects.kompotStandard)
                implementation(projects.formStandard)
            }
        }
    }
}

dependencies {
    add("kspDesktop", project(":kompot-registry-processor"))
}

ksp {
    arg("kompotModuleTag", "WizardClient")
}

kotlin.sourceSets.getByName("desktopMain") {
    kotlin.srcDir("build/generated/ksp/desktop/desktopMain/kotlin")
}
