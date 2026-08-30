plugins {
    kotlin("multiplatform")
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    id("ru.workinprogress.sborka.kmp")
    id("ru.workinprogress.sborka.publish")
}

kotlin {
    jvm("desktop")
    // Two Apple targets rather than the three the protocol modules carry: compose.runtime published
    // its last iosX64 artefact at 1.11.0-alpha01, so an Intel simulator is not reachable for anything
    // that depends on Compose (see the Targets section of the readme).
    iosArm64()
    iosSimulatorArm64()
    androidLibrary {
        namespace = "io.github.youndie.kompot.theme.client"
        compileSdk = 36
        minSdk = 24
    }
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            api(projects.kompotCore)
            // Ради самого контракта KompotDesignSystem/LocalKompotDesignSystem — он живет там же,
            // где рендереры, которые его дергают.
            api(projects.kompotClient)
            api(projects.kompotTheme)

            api(libs.compose.runtime)
            implementation(libs.compose.foundation)
            api(libs.compose.ui)
        }

        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
                // For the guard that asks which hooks this module's overlay declares itself: Java
                // reflection cannot tell an inherited default from an override, and that difference
                // is the whole question.
                implementation(kotlin("reflect"))
                // Compose UI-тесты рендерят реальное (офскрин) дерево через Skiko — нужен
                // рантайм текущей ОС, а не только API тестового фреймворка.
                implementation(compose.desktop.currentOs)
                implementation(libs.ui.test)
                // Готовые Material3-ключи (M3Colors.Primary и т.п.) для тестовых фикстур.
                implementation(projects.kompotDsMaterial)
            }
        }
    }
}
