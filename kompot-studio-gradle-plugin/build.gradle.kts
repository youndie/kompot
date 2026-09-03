plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    id("ru.workinprogress.sborka.jvm")
    id("ru.workinprogress.sborka.publish")
}

// The one module here that is a BUILD plugin rather than a library or an application. It ships no
// Kotlin a client runs — it registers a task that runs the studio's launcher on the consumer's own
// classpath, which is the only place the consumer's renderers exist.
gradlePlugin {
    plugins {
        create("kompotStudio") {
            id = "io.github.youndie.kompot.studio"
            implementationClass = "io.github.youndie.kompot.studio.gradle.KompotStudioPlugin"
            displayName = "kompot studio"
            description = "Adds a kompotStudio task that opens the screen editor on this build's renderers"
        }
    }
}

dependencies {
    compileOnly(gradleApi())
    // The `create`, `register`, `getByType` extensions this plugin is written with. They live in the
    // Kotlin DSL rather than in the Gradle API, and without them every one of those reads as an
    // unresolved reference on a type that is obviously right there.
    compileOnly(gradleKotlinDsl())
    // The Kotlin Gradle plugin's model, for reading a multiplatform target's compilation. compileOnly:
    // a build applying this one has the Kotlin plugin already, and bringing a second copy of it onto a
    // build classpath is how two versions of it meet.
    compileOnly(libs.kotlin.gradlePlugin)
}
