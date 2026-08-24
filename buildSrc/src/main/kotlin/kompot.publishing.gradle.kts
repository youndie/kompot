import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmVersion

plugins {
    `maven-publish`
}

// The group belongs to the convention, not to each module. Six modules of the Compose client arrived
// without one and were published under a group derived from the root project name — the failure
// surfaced only at upload time, as a PUT to the wrong path, after everything had compiled and tested.
group = "io.github.youndie"

// One version, reaching both the coordinate a consumer asks for and the name of the file that
// arrives. CI passes the full number through -PVERSION; gradle.properties holds the head of it so a
// local build and publishToMavenLocal work without extra parameters.
//
// Setting the publication version alone is not enough, and the difference is invisible here: the
// archive tasks take their file name from the PROJECT version, so every publish shipped a
// kompot-core-jvm-0.27.0.jar under the coordinate 0.27.0.46. Resolving works — the metadata points at
// the right url — and the file is simply misnamed on arrival, which makes two different releases
// indistinguishable to anything downstream that reads file names, under a version never released.
version = findProperty("VERSION")?.toString() ?: findProperty("kompot.version")?.toString() ?: "0.1.0"

plugins.withId("java") {
    extensions.configure<JavaPluginExtension> {
        withSourcesJar()
    }
}

// The KMP plugin registers publications on its own; the plain Kotlin/JVM plugin does not. Without
// this block a kotlin("jvm") module builds fine, its publish task reports success and uploads
// NOTHING — there is simply nothing to upload. Gradle's exit code cannot tell that apart from a
// successful publish; only asking the server whether the artifact resolves can.
// A platform registers no publication of its own either, and unlike a Kotlin/JVM module it has no
// sources to give away — only the component that carries its constraints.
plugins.withId("java-platform") {
    afterEvaluate {
        publishing.publications.create<MavenPublication>("maven") {
            from(components["javaPlatform"])
        }
    }
}

plugins.withId("org.jetbrains.kotlin.jvm") {
    afterEvaluate {
        publishing.publications.create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

// The floor, said out loud in the metadata. A plain kotlin("jvm") module gets this for free — the
// java plugin derives org.gradle.jvm.version from the toolchain — but a Kotlin Multiplatform module
// publishes its jvm variants with no such attribute at all, and those are the ones consumers actually
// take. Gradle then has nothing to refuse a too-old consumer with: resolution succeeds, compilation
// succeeds, and the failure arrives at class loading as UnsupportedClassVersionError, naming a
// bytecode version rather than this library.
//
// Declared here rather than left to the toolchain because the toolchain moving is precisely the event
// this exists to catch: with the attribute, a consumer gets "requires JVM runtime 17, you are on 11"
// at resolution time; without it, the floor moves silently with whatever JDK the build machine has.
plugins.withId("org.jetbrains.kotlin.multiplatform") {
    afterEvaluate {
        // Found by what a configuration IS rather than by what it is called. The obvious version of
        // this named "jvmApiElements" and "jvmRuntimeElements", which covers a jvm() target and
        // misses jvm("desktop") entirely — six Compose modules, the ones a client application
        // actually depends on. A configuration's name comes from its target, so a name is not a
        // property of the thing being looked for; the java-api/java-runtime usage is, and only a jvm
        // target carries it — every other target of a multiplatform module publishes kotlin-api.
        configurations
            .filter { configuration ->
                configuration.isCanBeConsumed &&
                    configuration.attributes.getAttribute(Category.CATEGORY_ATTRIBUTE)?.name == Category.LIBRARY &&
                    configuration.attributes.getAttribute(Usage.USAGE_ATTRIBUTE)?.name in setOf(Usage.JAVA_API, Usage.JAVA_RUNTIME)
            }.forEach { configuration ->
                configuration.attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, JVM_FLOOR)
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
