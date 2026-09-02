package io.github.youndie.kompot

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.youndie.kompot.forms.TextInputComponent
import kotlin.test.Test

// Size is a design decision like the shape beside it, and it is one a field asks for by role. Without
// it a deployment whose canvas draws 72-point inputs has the same two ways out the button had:
// replace a standard renderer to change one number, or ship the wrong height.
@OptIn(ExperimentalTestApi::class)
class FieldMetricsTest {
    private class SizingDesignSystem(private val minHeight: Dp = Dp.Unspecified) : KompotDesignSystem {
        @Composable
        override fun resolveColor(token: ColorToken): Color = Color.Black

        @Composable
        override fun resolveTypography(token: TypographyToken): TextStyle = TextStyle.Default

        @Composable
        override fun resolveSurface(role: SurfaceRole): KompotSurface =
            if (role == KompotSurfaceRoles.Field) KompotSurface(minHeight = minHeight) else KompotSurface()
    }

    @Test
    fun `a text field is as tall as the design system says`() =
        runFormsComposeUiTest {
            val controller = testFormController()

            setContent {
                MaterialTheme {
                    Column {
                        Field("silent", Dp.Unspecified, controller)
                        Field("tall", 96.dp, controller)
                    }
                }
            }

            // The control first: a design system that says nothing draws what it drew before. This
            // field with its supporting-text slot measures 80, and the case below asks for more than
            // that on purpose — a number under the control's own height would be swallowed by it, and
            // the assertion would pass without the design system having been consulted at all.
            onNodeWithTag("silent").assertHeightIsEqualTo(80.dp)
            onNodeWithTag("tall").assertHeightIsEqualTo(96.dp)
        }

    @Composable
    private fun Field(
        tag: String,
        minHeight: Dp,
        controller: io.github.youndie.kompot.form.FormController,
    ) {
        CompositionLocalProvider(LocalKompotDesignSystem provides SizingDesignSystem(minHeight)) {
            androidx.compose.foundation.layout.Box(Modifier.testTag(tag)) {
                TextInputRenderer().Render(
                    component = TextInputComponent(id = tag, fieldId = "name", label = "Name"),
                    actionHandler = recordingActionHandler(),
                    formController = controller,
                )
            }
        }
    }
}
