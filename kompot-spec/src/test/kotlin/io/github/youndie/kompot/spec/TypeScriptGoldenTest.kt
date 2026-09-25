package io.github.youndie.kompot.spec

import kotlinx.serialization.json.JsonObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The TypeScript declarations are generated from the same schemas the schema golden test holds, and
// committed beside them — so a schema change that forgets its types goes red here, in `check`, rather
// than in somebody's React client a release later.
class TypeScriptGoldenTest {
    private val files = mapOf(false to File("types/kompot.d.ts"), true to File("types/kompot.strict.d.ts"))

    private fun documents(): Map<String, JsonObject> {
        val schemas = KompotSpec.generateAll(KompotToolkitSpec.modules)
        return schemas.associate { it.fileName to it.document } +
            (KompotProtocol.PROFILE_FILE_NAME to KompotSpec.profile(schemas))
    }

    @Test
    fun `the TypeScript declarations equal what the generator prints from the schemas`() {
        val documents = documents()
        files.forEach { (strict, file) ->
            val printed = TypeScriptDeclarations.render(documents, strict)
            if (SchemaFiles.recordMode) {
                file.parentFile.mkdirs()
                file.writeText(printed)
                return@forEach
            }
            assertEquals(
                printed,
                file.takeIf { it.isFile }?.readText(),
                "${file.path} has drifted from the schema. Regenerate with ${SchemaFiles.RECORD_ENV}=true ./gradlew :kompot-spec:test",
            )
        }
    }

    // The part a hand-written union gets wrong: the open hierarchies keep a branch for the type this
    // build has never heard of, and the closed one does not.
    @Test
    fun `open hierarchies keep an unknown branch and the closed one does not`() {
        val printed = TypeScriptDeclarations.render(documents())
        assertTrue("| UnknownKompotComponent;" in printed, "KompotComponent is open")
        assertTrue("| UnknownKompotAction;" in printed, "KompotAction is open")
        assertTrue("UnknownKompotModifierNode" !in printed, "KompotModifierNode is closed (SPEC.md §2.3)")
        assertTrue("UnknownKompot" !in TypeScriptDeclarations.render(documents(), strict = true), "the strict file closes every hierarchy")
    }
}
