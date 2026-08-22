package io.github.youndie.kompot.ds.material

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import io.github.youndie.kompot.ColorToken
import io.github.youndie.kompot.ColumnRenderer
import io.github.youndie.kompot.KompotDesignSystem
import io.github.youndie.kompot.KompotModifierNode
import io.github.youndie.kompot.KompotSurface
import io.github.youndie.kompot.KompotSurfaceRoles
import io.github.youndie.kompot.LocalKompotDesignSystem
import io.github.youndie.kompot.LocalKompotRegistry
import io.github.youndie.kompot.SizeType
import io.github.youndie.kompot.SurfaceRole
import io.github.youndie.kompot.TypographyToken
import io.github.youndie.kompot.forms.ReadOnlyFieldComponent
import io.github.youndie.kompot.material3.M3Colors
import io.github.youndie.kompot.standard.ButtonComponent
import io.github.youndie.kompot.standard.CloseAction
import io.github.youndie.kompot.standard.ColumnComponent
import ru.workinprogress.viddik.annotations.ViddikScreenshot
import ru.workinprogress.viddik.core.viddikTypography

// A design that rounds nothing, fills its fields and forbids borders. None of it could be expressed:
// a Material button takes its shape from ButtonDefaults, not from MaterialTheme.shapes, so zeroing
// every Shapes slot changed not one pixel, and an outlined field's transparent container is not a
// theme role either. The only remedy was replacing the renderers.
private class SquareDesignSystem : KompotDesignSystem {
    @Composable
    override fun resolveColor(token: ColorToken): Color = Material3DesignSystem().resolveColor(token)

    @Composable
    override fun resolveTypography(token: TypographyToken): TextStyle = Material3DesignSystem().resolveTypography(token)

    @Composable
    override fun resolveSurface(role: SurfaceRole): KompotSurface =
        when (role) {
            KompotSurfaceRoles.Button -> KompotSurface(shape = SQUARE)
            KompotSurfaceRoles.button("quiet") -> KompotSurface(shape = SQUARE, container = Color(0xFFE7E0EC), content = Color(0xFF1D1B20))
            // A value, not an input: filled, and with the border a form control would have removed.
            KompotSurfaceRoles.ReadOnlyField ->
                KompotSurface(shape = SQUARE, container = Color(0xFFE8E6EA), content = Color(0xFF1D1B20), outline = Color.Transparent)
            else -> KompotSurface()
        }

    private companion object {
        val SQUARE: Shape = RoundedCornerShape(0)
    }
}

private val CONTROLS =
    ColumnComponent(
        id = "controls",
        modifiers =
            listOf(
                KompotModifierNode.Size(width = SizeType.Fill),
                KompotModifierNode.Background(M3Colors.Background),
                KompotModifierNode.Padding(all = 12),
            ),
        spacing = 10,
        children =
            listOf(
                ButtonComponent(id = "submit", text = "Submit", action = CloseAction),
                ButtonComponent(id = "cancel", text = "Cancel", action = CloseAction, variant = "quiet"),
                ReadOnlyFieldComponent(id = "status", label = "Status", value = "In review"),
            ),
    )

@Composable
private fun Controls(designSystem: KompotDesignSystem) {
    MaterialTheme(typography = viddikTypography()) {
        CompositionLocalProvider(
            LocalKompotDesignSystem provides designSystem,
            LocalKompotRegistry provides registry,
        ) {
            ColumnRenderer().Render(
                component = CONTROLS,
                actionHandler = recordingActionHandler(),
                formController = testFormController(),
            )
        }
    }
}

// The baseline: what a design system that answers nothing about surfaces gets, which is exactly what
// every existing one got before the hook existed.
@ViddikScreenshot(name = "Surface - Material defaults", group = "Renderer", width = 420, height = 220)
@Composable
fun MaterialSurfaceScreenshot() = Controls(Material3DesignSystem())

// The same tree, the same wire, one hook answered: square corners, a quiet button that reads as quiet,
// and a read-only field that no longer looks like something to type into.
@ViddikScreenshot(name = "Surface - answered by role", group = "Renderer", width = 420, height = 220)
@Composable
fun RoleSurfaceScreenshot() = Controls(SquareDesignSystem())
