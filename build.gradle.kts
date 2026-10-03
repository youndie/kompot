plugins {
    // The compiler and everything versioned with it come from `wip`, the catalog a sborka release
    // publishes: one number in gradle/libs.versions.toml moves all of them.
    alias(wip.plugins.kotlinJvm) apply false
    alias(wip.plugins.kotlinMultiplatform) apply false
    alias(wip.plugins.kotlinSerialization) apply false
    alias(wip.plugins.ksp) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(wip.plugins.composeCompiler) apply false
    // The Kotlin Multiplatform flavour of AGP: it adds an android target to a kotlin{} block rather
    // than asking a module to be an Android library that happens to share code, which is the wrong
    // way round for a toolkit whose modules are common code with no expect/actual anywhere.
    alias(wip.plugins.androidKotlinMultiplatformLibrary) apply false
    // The build conventions, declared here and applied per module. `apply false` for the same reason
    // as the Kotlin plugins above: the plugin lands on the build classpath once, and a module asking
    // for a versioned copy of something already there fails with a message about the classpath.
    alias(libs.plugins.sborkaJvm) apply false
    alias(libs.plugins.sborkaKmp) apply false
    alias(libs.plugins.sborkaLint) apply false
    alias(libs.plugins.sborkaPublish) apply false
}

// The group, the version, the toolchain and the whole publication lived here and in
// `buildSrc/src/main/kotlin/kompot.publishing.gradle.kts`. They come from `io.github.youndie.sborka`
// now — the same code, with the same reasons written into it, shared with the rest of the portfolio
// instead of copied — and the numbers behind them are one line each in `gradle.properties`.
//
// This file therefore declares plugins and nothing else. The toolchain in particular is no longer set
// here in an `afterEvaluate` over `subprojects`: `sborka.base`, which every module gets through
// `sborka.jvm`, `sborka.kmp` or `sborka.publish`, sets it on the module itself.
//
// How many wasm executables are linked at the same time is sborka's too (sborka#132): `sborka.base`
// puts every `KotlinJsIrLink` of a module with `kotlin.multiplatform` behind one build service with a
// single slot, so the links of a build run one after another in the Kotlin daemon instead of together.
// It was an exception here first (B-64) — eight links at once filled the daemon's 3 GB and failed
// uncached builds with "GC overhead limit exceeded"; one at a time peaked at about 2 GB — and moved to
// sborka unchanged, by task type, so a module that starts linking tomorrow is still counted. The
// switch is `sborka.serializeWebLinks`; leave it on unless the daemon's heap is re-measured.
