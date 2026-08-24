plugins {
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
}

// Java 17 for every module at once — the floor a consumer needs, not the JDK this happens to be
// built on.
//
// It used to say 25, which is what the machines here run, and that number left the building inside
// the bytecode: a consumer on 21 resolved the dependency without a word of complaint, compiled
// against it happily, and failed at class loading with UnsupportedClassVersionError — a message
// about a number rather than about kompot. Nobody building on 25 can see it.
//
// A toolchain rather than compilerOptions.jvmTarget, deliberately. jvmTarget only asks for older
// bytecode while still compiling against the newest JDK's class library, so a call to something
// added in 21 compiles and then fails on a 17 runtime. A toolchain compiles against 17's API, which
// turns "we use nothing newer" from an assumption into something the build checks.
//
// At once is not tidiness but a Gradle requirement: where the org.gradle.jvm.version attribute is
// present, a module on 17 cannot be built against a dependency on 25 — "looking for a library
// compatible with JVM runtime version 17, but ... is only compatible with JVM runtime version 25 or
// newer". So it is all of them or none.
//
// That attribute is what tells a consumer at resolution time; a plain kotlin("jvm") module gets it
// from this toolchain, a multiplatform one does not, and the kompot.publishing convention adds it there by hand.
subprojects {
    afterEvaluate {
        (extensions.findByName("kotlin") as? org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension)
            ?.jvmToolchain(JVM_FLOOR)
    }
}
