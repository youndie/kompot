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
            // kompot-client, а не наоборот — этот модуль потребляет KompotComponentRenderer/
            // LocalKompotDesignSystem (см. комментарий в kompot-images-client-coil/build.gradle.kts
            // про тот же принцип для картинок).
            implementation(projects.kompotClient)
            implementation(projects.kompotForms)
            implementation(projects.kompotRegistryAnnotations)
            // KompotComponentRenderer.Render принимает FormController в сигнатуре, и рендереры полей
            // сами читают/пишут состояние формы через FormController.collectFieldState и т.п.
            implementation(projects.formCore)
            implementation(projects.formStandard)

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
        }

        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
                // Compose UI-тесты рендерят реальное (офскрин) дерево через Skiko — нужен
                // рантайм текущей ОС, а не только API тестового фреймворка.
                implementation(compose.desktop.currentOs)
                implementation(libs.ui.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

dependencies {
    add("kspDesktop", project(":kompot-registry-processor"))
}

ksp {
    arg("kompotModuleTag", "FormsClient")
}

kotlin.sourceSets.getByName("desktopMain") {
    kotlin.srcDir("build/generated/ksp/desktop/desktopMain/kotlin")
}
