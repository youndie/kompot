package io.github.youndie.kompot.studio.export

import io.github.youndie.kompot.studio.KompotStudioConfig
import io.github.youndie.kompot.studio.SAMPLE_BODY
import io.github.youndie.kompot.studio.toolkitRegistry
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test

// HOW THE CHECKED-IN DRAFT IS REWRITTEN, and nothing else. It does nothing without the property, so
// an ordinary run never touches the source tree: the draft is there to be COMPILED, and a suite that
// regenerated it before checking it would be marking its own homework.
//
//   ./gradlew :kompot-studio:desktopTest --tests "*DraftRegenerator*" -Pdraft.out=<path>
class DraftRegenerator {
    @Test
    fun write() {
        val out = System.getProperty("draft.out") ?: return
        Path.of(out).writeText(
            exportDsl(
                KompotStudioConfig(registry = toolkitRegistry),
                Json.parseToJsonElement(SAMPLE_BODY),
                "io.github.youndie.kompot.studio.export",
                "sampleScreenDraft",
            ),
        )
    }
}
