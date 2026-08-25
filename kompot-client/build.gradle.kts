import org.gradle.kotlin.dsl.kotlin

plugins {
    kotlin("multiplatform")
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    kotlin("plugin.serialization")
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    id("kompot.publishing")
}
kotlin {
    jvm("desktop")
    androidLibrary {
        namespace = "io.github.youndie.kompot.client"
        compileSdk = 36
        minSdk = 24
        // The common tests run on this target too. They pass on jvm already and the modules are
        // common code with no expect/actual, so this is not about a second answer — it is about the
        // target that has none: without it the plugin skips them with a warning, and a suite nobody
        // runs on a platform is the same silence as a suite that does not exist.
        withHostTest {}
    }
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)

            api(projects.kompotCore)
            api(projects.kompotStandard)
            implementation(projects.kompotForms)
            api(projects.kompotAnalytics)
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
            api(projects.kompotRealtime)
            api(projects.formCore)
            // За PerformAction — действием, которое меняет доменное состояние без формы вокруг
            // (SPEC.md §16.4). Транспорта здесь по-прежнему нет: withPerform принимает отправку
            // лямбдой, ровно как withLoginSubmit.
            implementation(projects.kompotCommands)

            api(libs.compose.runtime)
            // api and not implementation: the public API of this module hands the type out, so a consumer
            // that cannot name it cannot call the function. The build, the tests and the publish stay green
            // either way — only somebody compiling against the artefact finds out (see #70).
            api(libs.compose.foundation)
            implementation(libs.compose.material3)
            api(libs.compose.ui)
            // NOT api, even though a consumer-side reader flagged five compose-resources types as
            // unreachable: they occur only in ActualResourceCollectorsKt, the accessor file Compose
            // Resources generates for this module's own resources. Public in the bytecode, invisible
            // to a Kotlin consumer, and nothing in the API hands one out.
            implementation(libs.compose.components.resources)
            implementation(libs.compose.components.ui.tooling.preview)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            // In commonTest rather than in desktopTest: the tests that use runTest live in
            // commonMain's test set, so every target that exists needs the dependency — a target
            // added later otherwise fails to compile tests that were there all along.
            implementation(libs.kotlinx.coroutines.test)
        }

        val desktopTest by getting {
            dependencies {
                // Compose UI-тесты рендерят реальное (офскрин) дерево через Skiko — нужен
                // рантайм текущей ОС, а не только API тестового фреймворка.
                implementation(compose.desktop.currentOs)
                implementation(libs.ui.test)
                // Готовые Material3-ключи (ColorToken.PRIMARY и т.п.) для тестовых фикстур —
                // сам kompot-client их не использует, только тесты рендереров.
                implementation(projects.kompotDsMaterial)
            }
        }
    }
}
