plugins {
    kotlin("jvm")
    id("kompot.publishing")
}


kotlin {
    jvmToolchain(JVM_FLOOR)
}

dependencies {
    implementation(libs.ksp.symbol.processing.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
}
