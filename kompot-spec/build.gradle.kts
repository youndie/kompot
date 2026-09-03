plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("ru.workinprogress.sborka.jvm")
    id("ru.workinprogress.sborka.publish")
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
