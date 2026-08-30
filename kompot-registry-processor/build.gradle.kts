plugins {
    kotlin("jvm")
    id("ru.workinprogress.sborka.jvm")
    id("ru.workinprogress.sborka.publish")
}


dependencies {
    implementation(libs.ksp.symbol.processing.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
}
