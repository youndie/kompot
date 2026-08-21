plugins {
    `maven-publish`
}

// The group belongs to the convention, not to each module. Six modules of the Compose client arrived
// without one and were published under a group derived from the root project name — the failure
// surfaced only at upload time, as a PUT to the wrong path, after everything had compiled and tested.
group = "io.github.youndie"

// The default version comes from gradle.properties so that a local build and publishToMavenLocal
// work without extra parameters. CI overrides it through -PVERSION (see afterEvaluate below),
// appending the run number.
version = findProperty("kompot.version")?.toString() ?: "0.1.0"

plugins.withId("java") {
    extensions.configure<JavaPluginExtension> {
        withSourcesJar()
    }
}

// The KMP plugin registers publications on its own; the plain Kotlin/JVM plugin does not. Without
// this block a kotlin("jvm") module builds fine, its publish task reports success and uploads
// NOTHING — there is simply nothing to upload. Gradle's exit code cannot tell that apart from a
// successful publish; only asking the server whether the artifact resolves can.
plugins.withId("org.jetbrains.kotlin.jvm") {
    afterEvaluate {
        publishing.publications.create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

publishing {
    repositories {
        maven {
            name = "wip"
            url = uri("https://reposilite.kotlin.website/snapshots")
            // /snapshots is readable anonymously; credentials are needed only for writing.
            credentials {
                username = findProperty("REPOSILITE_USER")?.toString()
                password = findProperty("REPOSILITE_SECRET")?.toString()
            }
        }
    }
}

afterEvaluate {
    findProperty("VERSION")?.toString()?.let { publishVersion ->
        publishing.publications.withType<MavenPublication>().configureEach {
            version = publishVersion
        }
    }
}
