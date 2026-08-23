package io.github.youndie.kompot.ds.material

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.youndie.kompot.ButtonRenderer
import io.github.youndie.kompot.ColorToken
import io.github.youndie.kompot.KompotDesignSystem
import io.github.youndie.kompot.KompotSurface
import io.github.youndie.kompot.LocalKompotDesignSystem
import io.github.youndie.kompot.LocalKompotRegistry
import io.github.youndie.kompot.SurfaceRole
import io.github.youndie.kompot.TypographyToken
import io.github.youndie.kompot.standard.ButtonComponent
import io.github.youndie.kompot.standard.CloseAction
import ru.workinprogress.viddik.annotations.ViddikScreenshot
import ru.workinprogress.viddik.core.viddikTypography

// A design system that has an opinion about the words on a button. Before the label could take one,
// it was drawn in whatever font the machine had: Material's own typography names no family, so the
// picture was stable on one machine and different on the next — and the label's width follows the
// font, so the two disagreed about the button's edge as well.
private class LoudButtonDesignSystem : KompotDesignSystem {
    @Composable
    override fun resolveColor(token: ColorToken): Color = Material3DesignSystem().resolveColor(token)

    @Composable
    override fun resolveTypography(token: TypographyToken): TextStyle = Material3DesignSystem().resolveTypography(token)

    @Composable
    override fun resolveSurface(role: SurfaceRole): KompotSurface =
        KompotSurface(
            // Copied from MaterialTheme so the bundled font survives: a bare TextStyle names no family
            // and would put this golden back in the platform's hands, which is the very defect it is
            // here to guard.
            textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
        )
}

@ViddikScreenshot(name = "Button - the design system sets the label", group = "Renderer", width = 300, height = 80)
@Composable
fun ButtonLabelFromDesignSystemScreenshot() {
    MaterialTheme(typography = viddikTypography()) {
        CompositionLocalProvider(
            LocalKompotDesignSystem provides LoudButtonDesignSystem(),
            LocalKompotRegistry provides registry,
        ) {
            ButtonRenderer().Render(
                component = ButtonComponent(id = "cta", text = "Place order", action = CloseAction),
                actionHandler = recordingActionHandler(),
                formController = testFormController(),
            )
        }
    }
}
