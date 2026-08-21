package io.github.youndie.kompot

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.form.FormSchema

// A stand-in design system: renderers resolve tokens through LocalKompotDesignSystem, and a UI test
// cares that something rendered and what text it shows, not about the exact colour or font.
private class TestDesignSystem : KompotDesignSystem {
    @Composable
    override fun resolveColor(token: ColorToken): Color = Color.Black

    @Composable
    override fun resolveTypography(token: TypographyToken): TextStyle = TextStyle.Default
}

fun testFormController(): FormController = FormController(FormSchema(formId = "test", fields = emptyList()))

fun recordingActionHandler(onAction: (KompotAction) -> Unit = {}) = KompotActionHandler { onAction(it) }

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

// A replacement for runDesktopComposeUiTest in every form-renderer test.
//
// runDesktopComposeUiTest does NOT install Dispatchers.Main, unlike an Android instrumented test,
// while collectAsStateWithLifecycle — which every form renderer reaches through collectFieldState —
// requires Dispatchers.Main.immediate and throws without it. One shared entry point instead of the
// same preamble in every test.
@OptIn(ExperimentalTestApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
fun runFormsComposeUiTest(block: suspend DesktopComposeUiTest.() -> Unit) {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    try {
        runDesktopComposeUiTest { block() }
    } finally {
        Dispatchers.resetMain()
    }
}
