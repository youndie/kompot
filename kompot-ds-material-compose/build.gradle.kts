plugins {
    kotlin("multiplatform")
    alias(wip.plugins.androidKotlinMultiplatformLibrary)
    kotlin("plugin.serialization")
    alias(libs.plugins.composeMultiplatform)
    alias(wip.plugins.composeCompiler)
    alias(wip.plugins.ksp)
    alias(libs.plugins.viddik)
    id("io.github.youndie.sborka.kmp")
    id("io.github.youndie.sborka.lint")
    alias(libs.plugins.dokka)
    id("io.github.youndie.sborka.publish")
}

kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation { }

    jvm("desktop")
    // Two Apple targets rather than the three the protocol modules carry: compose.runtime published
    // its last iosX64 artefact at 1.11.0-alpha01, so an Intel simulator is not reachable for anything
    // that depends on Compose (see the Targets section of the readme).
    iosArm64()
    iosSimulatorArm64()
    androidLibrary {
        namespace = "io.github.youndie.kompot.ds.material.compose"
        compileSdk = 37
        minSdk = 24
    }
    wasmJs {
        browser()
        // For the browser tests, not an application — see the note in :kompot-client (CMP-4906).
        binaries.executable()
    }

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
            // api and not implementation: the public API of this module hands the type out, so a consumer
            // that cannot name it cannot call the function. The build, the tests and the publish stay green
            // either way — only somebody compiling against the artefact finds out (see #70).
            api(libs.compose.material3)
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
                // PerformAction, for the test that a perform answering with show_message shows it.
                implementation(projects.kompotCommands)
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
                // The preview harness, so the whole-screen shots below go through the same path a
                // deployment's do — body in, real renderers, state as a parameter. Test scope: this
                // module publishes a design system, not a preview.
                implementation(projects.kompotPreview)
                // viddik itself (annotations, testing core, processor) is added by its plugin — see
                // the `viddik` block below.
            }
        }
    }
}

// The plugin puts the processor on `kspDesktopTest` and registers the generated sources — the two
// lines this file used to write by hand, and the pair that fails silently when one is misnamed (KSP
// reports SKIPPED and the screenshot task passes with no tests in it).
//
// verifyOnCheck is the one default overridden, and it is not optional: the plugin leaves goldens out
// of `check` by default, and before it they were ordinary tests of this module that `./gradlew build`
// ran. Taking the default would have kept CI green by no longer looking. The goldens are portable —
// every fixture draws with the bundled viddikTypography — so they belong in `check` on any host.
viddik {
    verifyOnCheck = true
}

// viddik 0.6 DECLARES Java 21 in its Gradle metadata (`org.gradle.jvm.version=21`); up to 0.1.1.8 it
// only shipped class file 65 and said nothing. Declared, it is a resolution error: a desktopTest
// classpath asking for Java 17 — the module's floor — finds no variant and the build stops before
// compiling anything. So the TEST classpaths ask for 21, which the toolchain the tests run on (25)
// clears; main code, and everything published, stays on the 17 floor.
configurations
    .matching { it.isCanBeResolved && it.name.startsWith("desktopTest") }
    .configureEach { attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21) }

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
