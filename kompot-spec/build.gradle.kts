plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("io.github.youndie.sborka.jvm")
    alias(libs.plugins.dokka)
    id("io.github.youndie.sborka.publish")
}


// JVM-only on purpose: the spec is a build-time and review-time artefact. It never travels into an
// iOS framework or a client application — the only runtime consumer of a schema is somebody else's
// server, and it gets finished .schema.json files rather than this module.
dependencies {
    // The generator reads the SerialDescriptors and polymorphic registrations of exactly the types
    // that travel on the wire. There is no second source of truth for the schema by design, so the
    // dependency is on the protocol modules themselves rather than on a copy of them.
    api(projects.kompotCore)
    // KompotComponentDoc: the prose a KSP processor carries from a type's KDoc to its schema. `api`
    // because it stands in KompotSpecModule's own signature — a build assembling a spec has to be able
    // to name it.
    api(projects.kompotRegistryAnnotations)
    implementation(projects.kompotStandard)
    implementation(projects.kompotForms)
    implementation(projects.formCore)
    implementation(projects.formStandard)
    implementation(projects.kompotImages)
    implementation(projects.kompotRealtime)
    implementation(projects.wizardCore)
    implementation(projects.kompotWizard)
    implementation(projects.kompotNavigation)
    implementation(projects.kompotAuth)
    implementation(projects.kompotCommands)
    implementation(projects.kompotTheme)
    api(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
}

// The schema goldens are an input of the test just as the classes are: without this, editing (or
// deleting) a schema/*.json does not re-run the test, and a spec that has drifted sails through CI
// as UP-TO-DATE.
//
// The spec travels in the jar as resources: that is how a consumer reads it from the classpath
// rather than by a relative path from someone else's working directory.
tasks.processResources {
    from("schema") { into("kompot-spec/schema") }
    // The prose too, and not for reading pleasure: a conformance case names the rule it holds by the
    // id §9 carries, and until now that id pointed at a document living only in this repository. A
    // reader on another language got the reference and no way to resolve it.
    from("SPEC.md") { into("kompot-spec") }
}

tasks.test {
    inputs
        .files(fileTree("schema"))
        .withPropertyName("schemaGoldens")
        .withPathSensitivity(org.gradle.api.tasks.PathSensitivity.RELATIVE)
}

// THE §15 CHECK — a task rather than a test, because it needs a second revision. A test sees one
// working tree; the question "is this change compatible?" has no meaning without the tree the change
// was made against, so the check reads that one out of git itself and stays out of `check`: a build
// on a machine with no history to compare against would otherwise fail for the wrong reason.
//
// Run it the way CI does:
//
//   ./gradlew :kompot-spec:checkSchemaCompatibility -Pkompot.compat.base=origin/main
tasks.register<JavaExec>("checkSchemaCompatibility") {
    group = "verification"
    description = "Classifies the change of schema/*.json against a base revision by the rules of SPEC.md §15"

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.github.youndie.kompot.spec.SchemaCompatibilityCliKt")

    // A revision rather than a branch name by default is the caller's business: CI passes the base of
    // the pull request, so that a run cannot be turned green by somebody else's merge into main.
    val base = providers.gradleProperty("kompot.compat.base").orElse("origin/main")
    argumentProviders.add(CommandLineArgumentProvider { listOf("--base", base.get()) })

    // The inputs of this task are the working tree AND a revision of the repository. Gradle watches
    // neither well enough to skip the run, and a skipped compatibility check reports the verdict of
    // the previous one.
    outputs.upToDateWhen { false }
}
