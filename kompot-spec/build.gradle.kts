plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("kompot.publishing")
}


// JVM-only on purpose: the spec is a build-time and review-time artefact. It never travels into an
// iOS framework or a client application — the only runtime consumer of a schema is somebody else's
// server, and it gets finished .schema.json files rather than this module.
dependencies {
    // The generator reads the SerialDescriptors and polymorphic registrations of exactly the types
    // that travel on the wire. There is no second source of truth for the schema by design, so the
    // dependency is on the protocol modules themselves rather than on a copy of them.
    implementation(projects.kompotCore)
    implementation(projects.kompotStandard)
    implementation(projects.kompotForms)
    implementation(projects.formCore)
    implementation(projects.kompotImages)
    implementation(projects.kompotRealtime)
    implementation(projects.wizardCore)
    implementation(projects.kompotWizard)
    implementation(projects.kompotNavigation)
    implementation(projects.kompotAuth)
    implementation(projects.kompotCommands)
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
}

tasks.test {
    inputs
        .files(fileTree("schema"))
        .withPropertyName("schemaGoldens")
        .withPathSensitivity(org.gradle.api.tasks.PathSensitivity.RELATIVE)
}
