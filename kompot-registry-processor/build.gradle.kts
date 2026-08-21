plugins {
    kotlin("jvm")
    id("kompot.publishing")
}


kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation(libs.ksp.symbol.processing.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
}
