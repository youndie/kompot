plugins {
    kotlin("multiplatform")
    id("kompot.publishing")
}



kotlin {
    jvm()

    sourceSets {
        commonMain {
            dependencies {
                api(projects.kompotCore)
                api(projects.formCore)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}
