plugins {
    kotlin("multiplatform")
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    kotlin("plugin.serialization")
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    id("ru.workinprogress.sborka.kmp")
    id("ru.workinprogress.sborka.publish")
}

kotlin {
    jvm("desktop")
    // Two Apple targets rather than three, for the reason every Compose module here has two:
    // compose.runtime published its last iosX64 artefact at 1.11.0-alpha01.
    iosArm64()
    iosSimulatorArm64()
    androidLibrary {
        namespace = "io.github.youndie.kompot.preview"
        compileSdk = 36
        minSdk = 24
    }
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            // api throughout: every one of these types appears in the signature of the single function
            // this module offers, and a consumer who cannot name a parameter cannot call it (#70).
            api(projects.kompotClient)
            api(projects.kompotCore)
            api(projects.formCore)
            // For KompotFormResponse — one of the two body shapes a screen arrives in. kompot-client
            // takes :kompot-forms as `implementation`, so naming it here is what puts the envelope on a
            // consumer's compile classpath rather than only on ours.
            api(projects.kompotForms)
            // The other shape: the envelope that carries a screen plus the topic its updates arrive on.
            api(projects.kompotRealtime)
            api(libs.compose.runtime)
            implementation(libs.kotlinx.serialization.json)
        }

        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
                // The form renderers, so a test draws a real form rather than a tree of nothing. Test
                // scope only: the harness itself must not carry a renderer set, or it would be
                // deciding what a deployment's registry contains.
                implementation(projects.kompotFormsClient)
                implementation(projects.kompotFormsStandard)
                implementation(projects.formStandard)
                // Compose UI tests render a real offscreen tree through Skiko, so the host's native
                // runtime is needed and not only the test framework's API. Never in a published source
                // set: currentOs would pin the host in the POM.
                implementation(compose.desktop.currentOs)
                implementation(libs.ui.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}
