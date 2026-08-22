plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ksp)
    id("kompot.publishing")
}

kotlin {
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            api(projects.kompotCore)
            // kompot-client, а не наоборот — этот модуль потребляет KompotComponentRenderer/
            // LocalKompotDesignSystem/LocalKompotRegistry, сам kompot-client про Coil ничего не знает
            // (см. комментарий в kompot-client/build.gradle.kts про синтетический KompotImageComponent).
            api(projects.kompotClient)
            api(projects.kompotImages)
            implementation(projects.kompotRegistryAnnotations)
            // KompotComponentRenderer.Render принимает FormController в сигнатуре — нужен на
            // компайл-класспасе любому модулю, реализующему интерфейс (kompot-client сам
            // подключает form-core как implementation, не api, поэтому это не транзитивно).
            api(projects.formCore)

            api(libs.compose.runtime)
            implementation(libs.compose.foundation)
            api(libs.compose.ui)

            implementation(libs.coil3.compose)
            implementation(libs.coil3.network.ktor3)
            implementation(libs.coil3.svg)
        }

        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

dependencies {
    add("kspDesktop", project(":kompot-registry-processor"))
}

ksp {
    arg("kompotModuleTag", "ImagesClient")
}

kotlin.sourceSets.getByName("desktopMain") {
    kotlin.srcDir("build/generated/ksp/desktop/desktopMain/kotlin")
}
