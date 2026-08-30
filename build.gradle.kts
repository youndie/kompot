plugins {
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
    // The build conventions, declared here and applied per module. `apply false` for the same reason
    // as the Kotlin plugins above: the plugin lands on the build classpath once, and a module asking
    // for a versioned copy of something already there fails with a message about the classpath.
    alias(libs.plugins.sborkaJvm) apply false
    alias(libs.plugins.sborkaKmp) apply false
    alias(libs.plugins.sborkaPublish) apply false
}

// The group, the version, the toolchain and the whole publication lived here and in
// `buildSrc/src/main/kotlin/kompot.publishing.gradle.kts`. They come from `ru.workinprogress.sborka`
// now — the same code, with the same reasons written into it, shared with the rest of the portfolio
// instead of copied — and the numbers behind them are one line each in `gradle.properties`.
//
// This file therefore declares plugins and nothing else. The toolchain in particular is no longer set
// here in an `afterEvaluate` over `subprojects`: `sborka.base`, which every module gets through
// `sborka.jvm`, `sborka.kmp` or `sborka.publish`, sets it on the module itself.
