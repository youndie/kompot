plugins {
    kotlin("multiplatform")
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    kotlin("plugin.serialization")
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.viddik)
    id("io.github.youndie.sborka.kmp")
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
        // 37 for the Compose half: Compose 1.12 brings androidx material3 1.5, whose AAR demands 37 of
        // everyone who depends on it, so consumers of this module are asked for it regardless. The
        // protocol modules stay on 36 — AGP publishes compileSdk as minCompileSdk, and they have no
        // dependency that asks for more (B-40; the same reasoning as :kompot-images-client-coil).
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

// viddik 0.6.0 adds its showroom directory (`build/generated/ksp/metadata/commonMain/kotlin`) to
// commonMain whether or not `showroomTargets` is on, and orders the KSP tasks after the one that
// writes it only when it IS on. With it off, every per-target KSP task reads a directory
// `kspCommonMainKotlinMetadata` produces without depending on it, and Gradle stops the build ("uses
// this output of task ... without declaring an explicit or implicit dependency"). This module has no
// showroom, so the directory is taken back out rather than ordered around. Harmless once viddik stops
// adding it (youndie/viddik#44).
kotlin.sourceSets.getByName("commonMain").kotlin.apply {
    setSrcDirs(srcDirs.filterNot { it.invariantSeparatorsPath.endsWith("build/generated/ksp/metadata/commonMain/kotlin") })
}

// viddik 0.6 DECLARES Java 21 in its Gradle metadata (`org.gradle.jvm.version=21`); up to 0.1.1.8 it
// only shipped class file 65 and said nothing. Declared, it is a resolution error: a desktopTest
// classpath asking for Java 17 — the module's toolchain — finds no variant and the build stops before
// compiling anything. So the TEST classpaths ask for 21, the same number the test launcher already
// runs on; main code, and everything published, stays on the 17 floor.
configurations
    .matching { it.isCanBeResolved && it.name.startsWith("desktopTest") }
    .configureEach { attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21) }

tasks.withType<Test>().configureEach {
    useJUnitPlatform()

    // The published bytecode of this module is Java 17 like every other module's (`sborka.jvmFloor`);
    // these TESTS are what needs a newer runtime. viddik ships class file 65 (Java 21), so on a 17
    // launcher the screenshot suite dies at class loading — the very failure mode the floor exists to
    // keep off consumers, arriving here from a dependency of the harness rather than from anything
    // published.
    //
    // Only the launcher moves. Compiling the tests on 17 against a newer class file is fine, and
    // keeping the compile there is what stops a test accidentally teaching main code to use an API
    // no consumer has.
    //
    // The number is written here rather than in a constant: it was one of two in `buildSrc`, and the
    // other one — the floor, which was the one repeated — is a line of `gradle.properties` now. This
    // one has exactly one reader, and a constant with one reader only hides where it is read.
    javaLauncher =
        javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(21)
        }
}
