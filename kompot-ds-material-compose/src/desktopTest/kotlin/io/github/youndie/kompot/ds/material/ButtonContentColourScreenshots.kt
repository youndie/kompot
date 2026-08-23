package io.github.youndie.kompot.ds.material

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import io.github.youndie.kompot.ButtonRenderer
import io.github.youndie.kompot.ColorToken
import io.github.youndie.kompot.KompotDesignSystem
import io.github.youndie.kompot.KompotSurface
import io.github.youndie.kompot.LocalKompotDesignSystem
import io.github.youndie.kompot.LocalKompotRegistry
import io.github.youndie.kompot.RowRenderer
import io.github.youndie.kompot.SurfaceRole
import io.github.youndie.kompot.TypographyToken
import io.github.youndie.kompot.standard.ButtonComponent
import io.github.youndie.kompot.standard.CloseAction
import io.github.youndie.kompot.standard.RowComponent
import ru.workinprogress.viddik.annotations.ViddikScreenshot
import ru.workinprogress.viddik.core.viddikTypography

// Two buttons whose surfaces name two different content colours, drawn under an ambient text style
// that names a third. If the label takes the ambient colour, both come out identical and the design
// cannot say which action is destructive — the fill is then the only channel emphasis has.
private class TwoToneButtons : KompotDesignSystem {
    @Composable
    override fun resolveColor(token: ColorToken): Color = Material3DesignSystem().resolveColor(token)

    @Composable
    override fun resolveTypography(token: TypographyToken): TextStyle = Material3DesignSystem().resolveTypography(token)

    @Composable
    override fun resolveSurface(role: SurfaceRole): KompotSurface =
        when (role.key) {
            "button.danger" -> KompotSurface(container = Color(0xFFF3E1E1), content = Color(0xFFB3261E))
            else -> KompotSurface(container = Color(0xFFE3E6F5), content = Color(0xFF1B3FA8))
        }
}

@ViddikScreenshot(name = "Button - the label takes its surface's content colour", group = "Renderer", width = 380, height = 80)
@Composable
fun ButtonContentColourScreenshot() {
    MaterialTheme(typography = viddikTypography()) {
        CompositionLocalProvider(
            LocalKompotDesignSystem provides TwoToneButtons(),
            LocalKompotRegistry provides registry,
            // A theme that names a text colour of its own — which is what a real one does, and what
            // makes the label's colour a question rather than a default.
            LocalTextStyle provides MaterialTheme.typography.labelLarge.copy(color = Color(0xFF3C4043)),
        ) {
            RowRenderer().Render(
                component =
                    RowComponent(
                        id = "actions",
                        spacing = 12,
                        children =
                            listOf(
                                ButtonComponent(id = "ok", text = "Save", action = CloseAction),
                                ButtonComponent(id = "rm", text = "Delete", action = CloseAction, variant = "danger"),
                            ),
                    ),
                actionHandler = recordingActionHandler(),
                formController = testFormController(),
            )
        }
    }
}
