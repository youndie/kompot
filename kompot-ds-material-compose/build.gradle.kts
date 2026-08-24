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
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.kompotCore)
            api(projects.kompotClient)
            // api, не implementation: потребители :kompot-ds-material-compose (см. sample/client)
            // авторят KOMPOT-деревья теми же ключами (ColorToken.PRIMARY и т.п.), что и
            // headless sample/server, — им нужен :kompot-ds-material на своем компайл-класспасе
            // тоже, а не только внутри этого модуля.
            api(projects.kompotDsMaterial)
            // api по той же причине, что и kompot-ds-material: приложение держит саму KompotTheme
            // (см. App.kt) и передает ее в toMaterialColorScheme из этого модуля.
            api(projects.kompotTheme)

            implementation(libs.compose.runtime)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
        }

        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(compose.desktop.currentOs)
                implementation(libs.ui.test)
                implementation(libs.kotlinx.coroutines.test)

                // Скриншот-тесты нескольких KompotComponentRenderer в "боевых" условиях: этот
                // модуль — единственное легитимное место для них (см. комментарий в
                // RendererScreenshots.kt) — реальная Material3DesignSystem живет тут, а не в
                // :kompot-client (тот зависит от kompot-ds-material-compose в обратную сторону, не наоборот).
                implementation(projects.kompotStandard)
                implementation(projects.kompotForms)
                // Рендереры :kompot-forms/:kompot-banking живут в этих sibling-модулях, а не в
                // kompot-client (см. @KompotComponentMarker/generatedFormsClientRenderers) — их нужно
                // подключить явно, чтобы registry в RendererScreenshots.kt их видел.
                implementation(projects.kompotFormsClient)
                // buildFormScreen/boundTextInput и т.д. — для скриншотов "сложных форм" (несколько
                // полей одним деревом, собранным ТЕМ ЖЕ DSL, что и реальные схемы в server/), а не
                // рендерингом одного KompotComponentRenderer.Render(...) за раз, как в остальном файле.
                implementation(projects.kompotFormsStandard)
                implementation(projects.formCore)
                implementation(projects.formStandard)
                // Голдены "одно дерево под разными темами" (ThemedRendererScreenshots.kt) —
                // единственное место, где виден результат server-driven темы, а не только
                // разрешение отдельного токена. Цикла нет: :kompot-theme-client зависит от
                // :kompot-client, но не от этого модуля.
                implementation(projects.kompotTheme)
                implementation(projects.kompotThemeClient)
                // Скриншот-тестер вынесен в отдельный проект viddik (соседний репозиторий) —
                // потребляется как внешняя библиотека из reposilite (см. комментарий у viddik-*
                // в libs.versions.toml), а не project(...).
                implementation(libs.viddik.annotations)
                implementation(libs.viddik.testing.core)
            }
        }
    }
}

dependencies {
    add("kspDesktopTest", libs.viddik.processor)
}

kotlin.sourceSets.getByName("desktopTest") {
    kotlin.srcDir("build/generated/ksp/desktop/desktopTest/kotlin")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()

    // The published bytecode of this module is Java 17 like every other module's; these TESTS are
    // what needs a newer runtime. viddik ships class file 65 (Java 21), so on a 17 launcher the
    // screenshot suite dies at class loading — the very failure mode JVM_FLOOR exists to keep off
    // consumers, arriving here from a dependency of the harness rather than from anything published.
    //
    // Only the launcher moves. Compiling the tests on 17 against a newer class file is fine, and
    // keeping the compile there is what stops a test accidentally teaching main code to use an API
    // no consumer has.
    javaLauncher =
        javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(SCREENSHOT_RUNTIME)
        }
}
