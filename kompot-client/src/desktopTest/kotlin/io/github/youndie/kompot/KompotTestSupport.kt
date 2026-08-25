package io.github.youndie.kompot

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.form.FormSchema

    // A stand-in design system: renderers resolve tokens through LocalKompotDesignSystem, and a UI
    // test cares that something rendered and what text it shows, not the exact colour or font.
internal class TestDesignSystem : KompotDesignSystem {
    @Composable
    override fun resolveColor(token: ColorToken): Color = Color.Black

    @Composable
    override fun resolveTypography(token: TypographyToken): TextStyle = TextStyle.Default
}

fun testFormController(): FormController = FormController(FormSchema(formId = "test", fields = emptyList()))

fun recordingActionHandler(onAction: (KompotAction) -> Unit = {}) = KompotActionHandler { onAction(it) }

    // No image or showcase renderers here: the tests that use those live in the modules that own
    // them, next to the renderers themselves.
@Composable
fun TestKompotTheme(content: @Composable () -> Unit) {
    MaterialTheme {
        CompositionLocalProvider(
            LocalKompotDesignSystem provides TestDesignSystem(),
            LocalKompotRegistry provides KompotRegistry(emptyMap()),
        ) {
            content()
        }
    }
}
