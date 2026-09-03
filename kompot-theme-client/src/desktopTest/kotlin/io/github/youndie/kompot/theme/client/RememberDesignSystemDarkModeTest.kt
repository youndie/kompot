package io.github.youndie.kompot.theme.client

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextStyle
import io.github.youndie.kompot.ColorToken
import io.github.youndie.kompot.KompotDesignSystem
import io.github.youndie.kompot.TypographyToken
import io.github.youndie.kompot.material3.M3Colors
import io.github.youndie.kompot.theme.kompotTheme
import kotlin.test.Test
import kotlin.test.assertEquals

// WHICH MODE THE WRAPPER DRAWS IN, and why it had to become a question a caller can answer.
//
// A brand has two halves — the design system every token resolves through, and the Material scheme
// every control reads — and they have to be told the same thing. The scheme's helper already took the
// mode; this one did not, so the two could be asked different questions: the scheme from the caller,
// the design system from the machine. The result is a light frame with a dark card under a light
// button, where each half is correct on its own.
@OptIn(ExperimentalTestApi::class)
class RememberDesignSystemDarkModeTest {
    private val theme =
        kompotTheme("two-tone") {
            light { color(M3Colors.Primary, "#FFFFFFFF") }
            dark { color(M3Colors.Primary, "#FF000000") }
        }

    @Test
    fun `an explicit mode decides, whatever the host is set to`() {
        val light = resolvedWith(darkMode = false)
        val dark = resolvedWith(darkMode = true)

        // Both, and in one test: either alone passes on a host that happens to be in that mode, which
        // is exactly how the defect this fixes stayed invisible on somebody's laptop.
        assertEquals(Color.White, light)
        assertEquals(Color.Black, dark)
    }

    @Test
    fun `null keeps the old answer, which is the system's`() {
        var expected: Boolean? = null
        val resolved =
            resolvedWith(darkMode = null) { expected = isSystemInDarkTheme() }

        // Compared with what the host actually says rather than with a constant: this assertion has to
        // hold on a dark machine and a light one, and pinning either would make it a test of the
        // machine it ran on.
        assertEquals(if (expected == true) Color.Black else Color.White, resolved)
    }

    // The result is captured outside because runDesktopComposeUiTest returns Unit: it runs a
    // composition rather than computing a value.
    private fun resolvedWith(
        darkMode: Boolean?,
        observe: @Composable () -> Unit = {},
    ): Color? {
        var resolved: Color? = null
        runDesktopComposeUiTest {
            setContent {
                observe()
                val designSystem = rememberKompotDesignSystem(theme, Fallback, darkMode)
                resolved = designSystem.resolveColor(M3Colors.Primary)
            }
            waitForIdle()
        }
        return resolved
    }

    private object Fallback : KompotDesignSystem {
        @Composable
        override fun resolveColor(token: ColorToken): Color = Color.Magenta

        @Composable
        override fun resolveTypography(token: TypographyToken): TextStyle = TextStyle.Default
    }
}
