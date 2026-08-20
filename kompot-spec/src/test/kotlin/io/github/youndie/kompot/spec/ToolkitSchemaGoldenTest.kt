package io.github.youndie.kompot.spec

import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The golden test of the toolkit's own schema: the files in kompot-spec/schema/ must equal what the
// generator prints from the current Kotlin types. This is what stops the spec falling quietly behind
// the code — add a component, forget to regenerate, and the build goes red.
//
// The set here is the toolkit's own (KompotToolkitSpec.modules), so the profile beside it is the
// closed list of a build that uses nothing but this repository. An application's profile is a
// superset and is generated in the application's own build.
class ToolkitSchemaGoldenTest {
    private fun generated(): Map<String, JsonObject> {
        val schemas = KompotSpec.generateAll(KompotToolkitSpec.modules)
        return schemas.associate { it.fileName to it.document } +
            (KompotProtocol.PROFILE_FILE_NAME to KompotSpec.profile(schemas))
    }

    @Test
    fun `the schema files equal what the generator prints from the Kotlin types`() {
        val documents = generated()

        if (SchemaFiles.recordMode) {
            documents.forEach { (fileName, document) -> SchemaFiles.write(fileName, document) }
            return
        }

        val stale = documents.filterKeys { fileName -> SchemaFiles.read(fileName) != SchemaFiles.render(documents.getValue(fileName)) }
        assertTrue(
            stale.isEmpty(),
            "The schema has drifted from the code: ${stale.keys.sorted()}. " +
                "Regenerate with ${SchemaFiles.RECORD_ENV}=true ./gradlew :kompot-spec:test",
        )
    }

    @Test
    fun `the schema directory holds no file without a source`() {
        // A module dropped from the spec must take its file with it, or another team keeps
        // implementing something the protocol no longer has.
        if (!SchemaFiles.recordMode) assertEquals(generated().keys.sorted(), SchemaFiles.names())
    }
}
