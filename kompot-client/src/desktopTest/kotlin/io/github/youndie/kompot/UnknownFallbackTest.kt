package io.github.youndie.kompot

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import io.github.youndie.kompot.standard.TextComponent
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.plus
import io.github.youndie.kompot.standard.kompotStandardSerializersModule
import io.github.youndie.kompot.generated.generatedStandardSerializersModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private val json =
    Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        serializersModule = kompotCoreSerializersModule + kompotStandardSerializersModule + generatedStandardSerializersModule
    }

// A server that replaces a toolkit component with one of its own knows the exact stand-in and had no
// way to say so. The substitution was entirely the client's, and the client knows only that something
// was unfamiliar.
@OptIn(ExperimentalTestApi::class)
class UnknownFallbackTest {
    private val body =
        """
        {"type":"deployment_specific","id":"x","fallback":{"type":"text","id":"t","text":"Plain instead"}}
        """.trimIndent()

    @Test
    fun `an unfamiliar type keeps the equivalent the server named`() {
        val decoded = json.decodeFromString(PolymorphicSerializer(KompotComponent::class), body)

        assertIs<UnknownComponent>(decoded)
        assertEquals("deployment_specific", decoded.originalType)
        assertEquals(TextComponent(id = "t", text = "Plain instead"), decoded.fallback)
    }

    @Test
    fun `the fallback is what reaches the screen`() =
        runDesktopComposeUiTest {
            val decoded = json.decodeFromString(PolymorphicSerializer(KompotComponent::class), body)
            setContent { WithRenderers { LocalKompotRegistry.current.RenderNode(decoded, recordingActionHandler(), testFormController()) } }

            onNodeWithText("Plain instead").assertIsDisplayed()
        }

    // Without one the node is skipped exactly as before: this adds a way to say something, not a new
    // obligation to say it.
    @Test
    fun `an unfamiliar type with no fallback still decodes to a placeholder`() {
        val decoded = json.decodeFromString(PolymorphicSerializer(KompotComponent::class), """{"type":"nonesuch","id":"x"}""")

        assertIs<UnknownComponent>(decoded)
        assertEquals(null, decoded.fallback)
    }
}

@Composable
private fun WithRenderers(content: @Composable () -> Unit) {
    TestKompotTheme {
        CompositionLocalProvider(
            LocalKompotRegistry provides KompotRegistry(kompotCoreRenderers + kompotStandardRenderers),
            content = content,
        )
    }
}
