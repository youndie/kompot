plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("kompot.publishing")
}

// The corpus files stay where they are — a plain directory anybody can read, diff and copy into
// another language — and are ALSO packaged into the jar. Without this the artefact carries the runner
// and not one case, so a Kotlin consumer would have to vendor the JSON, and a vendored copy is how a
// corpus quietly stops matching the specification it came from.
sourceSets.main {
    resources.srcDir("corpus")
}

dependencies {
    // Only JSON. The corpus is data and the adapter is the implementer's: nothing here decodes a form
    // into Kotlin types, because the moment it did, the contract would stop being one a client on
    // another language could satisfy.
    //
    // api, and here it is not a nicety: JsonObject is the vocabulary of KompotFormClient itself —
    // every value crossing the adapter boundary is one — so a consumer who cannot name the type
    // cannot implement the interface at all. It appears in thirty public member descriptors of this
    // artefact, and as `implementation` a consumer received none of them.
    api(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    // Only to GENERATE the case-format schema that ships beside the cases, and only in tests: the
    // artefact carries the finished .schema.json, not the generator. Same posture the schemas of the
    // wire take towards :kompot-spec.
    testImplementation(projects.kompotSpec)
    testImplementation(projects.formCore)
    testImplementation(projects.formStandard)
    testImplementation(libs.kotlinx.coroutines.test)
}
