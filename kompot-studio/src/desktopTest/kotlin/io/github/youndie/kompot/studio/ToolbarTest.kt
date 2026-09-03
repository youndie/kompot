package io.github.youndie.kompot.studio

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import io.github.youndie.kompot.LocalKompotDesignSystem
import io.github.youndie.kompot.ds.material.Material3DesignSystem
import io.github.youndie.kompot.form.standard.AmountValue
import io.github.youndie.kompot.form.standard.BooleanValue
import io.github.youndie.kompot.form.standard.EntityValue
import io.github.youndie.kompot.form.standard.TextValue
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.CloseAction
import kotlinx.serialization.json.Json
import ru.workinprogress.viddik.core.captureComposable
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

// The switches over the frame. Each one changes exactly one parameter of an API that already exists,
// which is the argument for having them at all — and the reason each is testable without a window.
@OptIn(ExperimentalTestApi::class)
class ToolbarTest {
    private val config = KompotStudioConfig(registry = toolkitRegistry)

    @Test
    fun `filled takes a value per field, typed by what the wire says the field is`() {
        val body =
            """
            {"schema":{"formId":"f","fields":[
                {"type":"text_field","fieldId":"name","rules":[]},
                {"type":"amount_field","fieldId":"sum","rules":[]},
                {"type":"checkbox_field","fieldId":"agree","rules":[]},
                {"type":"selection_field","fieldId":"account","rules":[]},
                {"type":"esim_slider_field","fieldId":"gigabytes","rules":[]}
              ]},
             "screen":{"type":"column","id":"root","children":[]}}
            """.trimIndent()

        val values = previewState(FormState.FILLED, Json.parseToJsonElement(body)).values

        assertEquals(TextValue("Sample"), values["name"])
        assertEquals(AmountValue(1_000L), values["sum"])
        assertEquals(BooleanValue(true), values["agree"])
        assertEquals(EntityValue("sample", "Sample"), values["account"])
        // A deployment's own field type: text, which is what an unfamiliar field most likely is. The
        // alternative — skipping it — would leave a form that says "filled" and is not.
        assertEquals(TextValue("Sample"), values["gigabytes"])
    }

    @Test
    fun `errors touches every field and fills none`() {
        val state = previewState(FormState.ERRORS, null)

        // Nothing typed and everything touched: errors show on a field somebody has LEFT, so an
        // untouched form looks valid however empty it is.
        assertTrue(state.allFieldsChanged)
        assertEquals(emptyMap(), state.values)

        val empty = previewState(FormState.EMPTY, null)
        assertTrue(!empty.allFieldsChanged, "the empty picture pre-touched the form")
    }

    @Test
    fun `a device preset confines the screen, and the window preset does not`() {
        // A full-bleed background, so the painted area IS the measurement.
        val body =
            """
            {"type":"column","id":"root",
             "modifiers":[{"type":"size","width":"Fill","height":"Fill"},
                          {"type":"background","color":"primary"}],
             "children":[]}
            """.trimIndent()

        val full = paint(capture(body, DEVICE_PRESETS.first()))
        val confined = paint(capture(body, DevicePreset("240×320", 240, 320)))

        assertTrue(full > 0, "the window preset painted nothing at all")
        // Strictly less, and by roughly the width it was denied: a preset that did not confine would
        // paint exactly as much, and the switch would be decoration. This is the failure a fixed
        // design meets on a short window, made visible before a phone does it.
        assertTrue(confined < full, "the 360-wide preset painted $confined of $full — it did not confine")
    }

    @Test
    fun `a navigate carries the deeplink the log can follow, and nothing else does`() {
        assertEquals("app://home", LoggedAction("00:00:00", NavigateAction("app://home")).deeplink)
        // The negative half: everything else is written down and not acted on, because acting would
        // mean the studio performing a side effect from a screen nobody has shipped.
        assertNull(LoggedAction("00:00:00", CloseAction).deeplink)
    }

    @Test
    fun `pressing a button in the frame reaches the handler the window logs with`() {
        val body =
            """{"type":"button","id":"cta","text":"Order","action":{"type":"navigate","deeplink":"app://pay"}}"""
        val logged = mutableListOf<LoggedAction>()

        runDesktopComposeUiTest {
            setContent {
                MaterialTheme {
                    StudioRenderPane(
                        config = config,
                        body = body,
                        brand = null,
                        dark = false,
                        actionHandler = { action -> logged += LoggedAction("00:00:00", action) },
                    ) { kind, type -> fail("the sample body degraded: $kind $type") }
                }
            }
            onNodeWithText("Order").performClick()
        }

        // The whole claim of the log: a tap that goes nowhere in a preview is the half of a screen
        // nobody can see, and this is it arriving.
        assertEquals(1, logged.size, "the tap did not reach the handler")
        assertEquals("app://pay", logged.single().deeplink)
    }

    // A frame with a colour of its own, so the measurement does not depend on guessing what stock
    // Material3 resolves `primary` to — a number that moves with the library and would turn this into
    // a test of the palette.
    private val measuredConfig =
        KompotStudioConfig(
            registry = toolkitRegistry,
            frame = { _, _, content ->
                MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF000000 or MEASURED.toLong()))) {
                    Surface(Modifier.fillMaxSize()) {
                        CompositionLocalProvider(LocalKompotDesignSystem provides Material3DesignSystem()) {
                            content()
                        }
                    }
                }
            },
        )

    private fun capture(
        body: String,
        preset: DevicePreset,
    ): BufferedImage =
        captureComposable(width = 420, height = 420, compositionLocals = emptyList()) {
            StudioRenderPane(
                config = measuredConfig,
                body = body,
                brand = null,
                dark = false,
                device = preset,
            ) { kind, type -> fail("the sample body degraded: $kind $type") }
        }

    private fun paint(image: BufferedImage): Int {
        var painted = 0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (image.getRGB(x, y) and 0xFFFFFF == MEASURED) painted++
            }
        }
        return painted
    }

    private companion object {
        const val MEASURED = 0x7B1FA2
    }
}
