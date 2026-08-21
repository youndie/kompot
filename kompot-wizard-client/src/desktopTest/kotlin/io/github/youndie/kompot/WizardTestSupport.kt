package io.github.youndie.kompot

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import io.github.youndie.kompot.standard.TextComponent
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.form.FormSchema

private class TestDesignSystem : KompotDesignSystem {
    @Composable
    override fun resolveColor(token: ColorToken): Color = Color.Black

    @Composable
    override fun resolveTypography(token: TypographyToken): TextStyle = TextStyle.Default
}

fun testFormController(): FormController = FormController(FormSchema(formId = "test", fields = emptyList()))

fun recordingActionHandler(onAction: (KompotAction) -> Unit = {}) = KompotActionHandler { onAction(it) }

// WizardScreenRenderer delegates its content through LocalKompotRegistry.RenderNode, so the real
// TextRenderer — already a commonMain dependency of this module — is enough to assemble a step out of
// a TextComponent in a test, with no synthetic stand-ins.
@Composable
fun TestKompotTheme(content: @Composable () -> Unit) {
    MaterialTheme {
        CompositionLocalProvider(
            LocalKompotDesignSystem provides TestDesignSystem(),
            LocalKompotRegistry provides KompotRegistry(mapOf(TextComponent::class to TextRenderer())),
        ) {
            content()
        }
    }
}
