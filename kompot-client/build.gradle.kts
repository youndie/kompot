import org.gradle.kotlin.dsl.kotlin

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    id("kompot.publishing")
}
kotlin {
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)

            implementation(projects.kompotCore)
            implementation(projects.kompotStandard)
            implementation(projects.kompotForms)
            implementation(projects.kompotAnalytics)
            // Формные и банковские рендереры переехали в :kompot-forms-client/:kompot-banking-client —
            // kompot-client теперь зависит от kompot-standard/forms/banking только за их
            // компонентами/generated*SerializersModule (KSP), не за Compose-рендерерами.
            // KompotImageComponent (чистый Kotlin, без Coil/Compose) нужен только для
            // generatedImagesSerializersModule в kompotJsonConfig (см. KompotClient.kt) — конкретный
            // рендерер картинок в :kompot-images-client-coil, kompot-client про Coil не знает.
            implementation(projects.kompotImages)
            // Только за generatedWizardSerializersModule/kompotWizardSerializersModule в
            // kompotJsonConfig (см. KompotClient.kt) — конкретный рендерер шага визарда в
            // :kompot-wizard-client, тот же принцип, что у kompot-banking/kompot-forms выше.
            implementation(projects.kompotWizard)
            // Только протокол (UpdateComponentMessage) + KompotRealtimeSource —
            // никакого Ktor/SSE здесь, конкретный транспорт в sample/client (см. Realtime.kt).
            implementation(projects.kompotRealtime)
            implementation(projects.formCore)
            // За PerformAction — действием, которое меняет доменное состояние без формы вокруг
            // (SPEC.md §16.4). Транспорта здесь по-прежнему нет: withPerform принимает отправку
            // лямбдой, ровно как withLoginSubmit.
            implementation(projects.kompotCommands)

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.components.ui.tooling.preview)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        val desktopTest by getting {
            dependencies {
                // Compose UI-тесты рендерят реальное (офскрин) дерево через Skiko — нужен
                // рантайм текущей ОС, а не только API тестового фреймворка.
                implementation(compose.desktop.currentOs)
                implementation(libs.ui.test)
                implementation(libs.kotlinx.coroutines.test)
                // Готовые Material3-ключи (ColorToken.PRIMARY и т.п.) для тестовых фикстур —
                // сам kompot-client их не использует, только тесты рендереров.
                implementation(projects.kompotDsMaterial)
            }
        }
    }
}
