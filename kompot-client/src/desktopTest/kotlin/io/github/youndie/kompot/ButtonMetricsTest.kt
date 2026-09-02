package io.github.youndie.kompot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.width
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.youndie.kompot.standard.ButtonComponent
import io.github.youndie.kompot.standard.CloseAction
import kotlin.test.Test
import kotlin.test.assertEquals

// A design system could shape a button and paint it but not size it: every button came out at
// Material's own height whatever the design asked for, and the ways round it were forking a standard
// renderer to change one number, or leaving the height wrong.
@OptIn(ExperimentalTestApi::class)
class ButtonMetricsTest {
    private class SizingDesignSystem(
        private val minHeight: Dp = Dp.Unspecified,
        private val contentPadding: PaddingValues? = null,
    ) : KompotDesignSystem {
        @Composable
        override fun resolveColor(token: ColorToken): Color = Color.Black

        @Composable
        override fun resolveTypography(token: TypographyToken): TextStyle = TextStyle.Default

        @Composable
        override fun resolveSurface(role: SurfaceRole): KompotSurface =
            if (role == KompotSurfaceRoles.Button) {
                KompotSurface(minHeight = minHeight, contentPadding = contentPadding)
            } else {
                KompotSurface()
            }
    }

    @Composable
    private fun Button(
        tag: String,
        designSystem: KompotDesignSystem,
    ) {
        CompositionLocalProvider(LocalKompotDesignSystem provides designSystem) {
            androidx.compose.foundation.layout.Box(Modifier.testTag(tag)) {
                ButtonRenderer().Render(
                    component = ButtonComponent(id = "b", text = "Pay", action = CloseAction),
                    actionHandler = recordingActionHandler(),
                    formController = testFormController(),
                )
            }
        }
    }

    @Test
    fun `a button is as tall as the design system says`() =
        runDesktopComposeUiTest {
            setContent {
                MaterialTheme {
                    Column {
                        Button("silent", SizingDesignSystem())
                        Button("tall", SizingDesignSystem(minHeight = 56.dp))
                    }
                }
            }

            // The control comes first and is half the point: a design system that says nothing gets
            // exactly the button it got before, which is what lets this ship without moving a screen
            // that never asked.
            //
            // 48 rather than the 40 a Material button draws: the measurement is of the slot, and
            // Material pads every clickable up to the 48dp interactive minimum. Which is also why the
            // case below asks for 56 — anything under 48 would be swallowed by that floor and the
            // assertion would pass without the design system having been consulted at all.
            onNodeWithTag("silent").assertHeightIsEqualTo(48.dp)
            onNodeWithTag("tall").assertHeightIsEqualTo(56.dp)
        }

    // Padding is the other half of a control's size, and on a button it is the half that decides the
    // width. Asserted as a DIFFERENCE rather than as a number of dp: the label's own width follows
    // whatever font the machine resolved, so an absolute width would be measuring the font.
    @Test
    fun `a button carries the content padding the design system gives it`() =
        runDesktopComposeUiTest {
            setContent {
                MaterialTheme {
                    Column {
                        Button("default", SizingDesignSystem())
                        Button("roomy", SizingDesignSystem(contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp)))
                    }
                }
            }

            val default = onNodeWithTag("default").getUnclippedBoundsInRoot().width
            val roomy = onNodeWithTag("roomy").getUnclippedBoundsInRoot().width

            // Material's own horizontal padding is 24 either side; 48 either side is 24 more twice.
            assertEquals(48.dp.value, (roomy - default).value, 0.5f)
        }
}
