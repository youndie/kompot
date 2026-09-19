plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    id("io.github.youndie.sborka.kmp")
}

// THE ONLY MODULE HERE THAT IS PUBLISHED NOWHERE, and that is the point of it rather than an
// omission: it is a page, not a library. `io.github.youndie.sborka.publish` is deliberately absent —
// with it the module would enter :kompot-bom and the three publication audits would start asking it
// for the metadata of an artefact nobody resolves.
//
// One target, and it is the one the client already declares: kompot-client, kompot-preview,
// kompot-forms-client, kompot-theme-client and kompot-ds-material-compose all carry
// `wasmJs { browser() }`. The showcase is therefore a distribution of code that already builds, which
// is why it is worth having at all.
kotlin {
    wasmJs {
        browser()
        // What makes this an application: without it the target compiles a library and there is
        // nothing to serve.
        binaries.executable()
    }

    sourceSets {
        val wasmJsMain by getting {
            dependencies {
                // The rendering path a client takes, and nothing else: the registry and its dispatch,
                // the standard renderers, and the seam that turns a body into a frame. A page that
                // drew screens any other way would be a picture of a different product.
                implementation(projects.kompotClient)
                implementation(projects.kompotStandard)
                implementation(projects.kompotPreview)
                // The design system the renderers resolve tokens through. Material3 rather than a
                // brand of our own: the toolkit ships this one, and a showcase brand would have to be
                // explained before anything else on the page could be.
                implementation(projects.kompotDsMaterialCompose)

                implementation(libs.compose.runtime)
                implementation(libs.compose.foundation)
                implementation(libs.compose.material3)
                implementation(libs.compose.ui)
            }
        }
    }
}
