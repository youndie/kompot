plugins {
    kotlin("jvm")
    id("io.github.youndie.sborka.jvm")
    id("io.github.youndie.sborka.publish")
}


dependencies {
    implementation(libs.ksp.symbol.processing.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
}
