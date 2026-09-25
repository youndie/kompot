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
// `buildSrc/src/main/kotlin/kompot.publishing.gradle.kts`. They come from `io.github.youndie.sborka`
// now — the same code, with the same reasons written into it, shared with the rest of the portfolio
// instead of copied — and the numbers behind them are one line each in `gradle.properties`.
//
// This file therefore declares plugins and nothing else. The toolchain in particular is no longer set
// here in an `afterEvaluate` over `subprojects`: `sborka.base`, which every module gets through
// `sborka.jvm`, `sborka.kmp` or `sborka.publish`, sets it on the module itself.

// ONE EXCEPTION to "plugins and nothing else", and it has to be here because it is about all modules
// at once: how many wasm executables are linked at the same time (B-64).
//
// Eight modules link a production wasm executable, the configuration cache runs their tasks in
// parallel, and every link runs inside the one Kotlin daemon. A single link holds about 800 MB (the
// playground, measured with -Xlog:gc); eight together did not fit the daemon's 3 GB, which then spent
// an hour in back-to-back full collections at 2.78 of 2.9 GB before failing with "GC overhead limit
// exceeded" — every full build on the Linux box, and once on CI. Raising the heap would move the
// ceiling to wherever the ninth module puts it, and on a runner whose memory nobody here measured; a
// fixed number of links at a time keeps the daemon's need where it is however many modules link.
//
// One at a time, measured: three uncached full builds each way on the Linux box, the daemon's live
// heap after GC at its peak. Two links at a time peaked at 2161, 2880 and 2194 MB of 3072 — the second
// run a hand's width from the ceiling; one at a time at 2034 and 2025 MB, for about a minute more of a
// four-to-five-minute build. The next module that links would have taken the margin two left.
//
// By task type rather than by a list of modules, so a module that starts linking tomorrow is counted
// without anybody remembering this file.
abstract class WasmLinkSlots : BuildService<BuildServiceParameters.None>

val wasmLinkSlots =
    gradle.sharedServices.registerIfAbsent("wasmLinkSlots", WasmLinkSlots::class) {
        maxParallelUsages.set(1)
    }

subprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrLink>().configureEach {
        usesService(wasmLinkSlots)
    }
}
