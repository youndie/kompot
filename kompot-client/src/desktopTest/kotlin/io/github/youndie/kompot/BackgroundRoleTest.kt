package io.github.youndie.kompot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.TextComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// A server could not compose a card: a background painted a rectangle, and the only way to the
// rounded filled group every design draws was a component type that hard-codes the grouping — a
// layout property smuggled into a product's dictionary of wire types.
@OptIn(ExperimentalTestApi::class)
class BackgroundRoleTest {
    private val fill = Color(0xFF2E7D32)

    private inner class CardDesignSystem(private val cardShape: Shape?) : KompotDesignSystem {
        @Composable
        override fun resolveColor(token: ColorToken): Color = fill

        @Composable
        override fun resolveTypography(token: TypographyToken): TextStyle = TextStyle.Default

        @Composable
        override fun resolveSurface(role: SurfaceRole): KompotSurface =
            if (role == KompotSurfaceRoles.Container) KompotSurface(shape = cardShape) else KompotSurface()
    }

    private fun card(role: String?) =
        ColumnComponent(
            id = "card",
            modifiers =
                listOf(
                    KompotModifierNode.Size(widthDp = 120, heightDp = 120),
                    KompotModifierNode.Background(ColorToken("surface_raised"), role = role),
                ),
            children = listOf(TextComponent(id = "t", text = "Balance")),
        )

    private fun corner(image: ImageBitmap): Color = image.toPixelMap()[2, 2]

    // Every case asks the corner a question whose answer means nothing unless the card was painted at
    // all: "the corner is not the fill" is also true of a screen where nothing happened. So each one
    // checks the middle of the card too, and the check is the same in all four.
    private fun centre(image: ImageBitmap): Color = image.toPixelMap()[60, 60]

    private fun ComposeScene(
        role: String?,
        cardShape: Shape?,
    ): @Composable () -> Unit =
        {
            MaterialTheme(shapes = Shapes()) {
                CompositionLocalProvider(
                    LocalKompotDesignSystem provides CardDesignSystem(cardShape),
                    LocalKompotRegistry provides KompotRegistry(kompotCoreRenderers + kompotStandardRenderers),
                ) {
                    Box(Modifier.size(200.dp)) {
                        ColumnRenderer().Render(card(role), recordingActionHandler(), testFormController())
                    }
                }
            }
        }

    @Test
    fun `a background naming a role takes that surface's corner`() =
        runDesktopComposeUiTest(width = 200, height = 200) {
            setContent(ComposeScene(role = "container", cardShape = RoundedCornerShape(24.dp)))

            val image = onRoot().captureToImage()
            assertEquals(fill, centre(image), "nothing was painted, so the corner says nothing")
            assertTrue(corner(image) != fill, "the corner is filled, so the fill is still a rectangle")
        }

    // The control, and the compatibility question in one: every tree written before this names no
    // role and must keep its square corner.
    @Test
    fun `without a role the fill is the rectangle it always was`() =
        runDesktopComposeUiTest(width = 200, height = 200) {
            setContent(ComposeScene(role = null, cardShape = RoundedCornerShape(24.dp)))

            val image = onRoot().captureToImage()
            assertEquals(fill, centre(image))
            assertEquals(fill, corner(image), "a background with no role stopped painting the corner")
        }

    // An unfamiliar name costs the corner, not the screen: the same degradation an unknown component
    // gets, one field down.
    @Test
    fun `a role the design system does not know keeps the rectangle`() =
        runDesktopComposeUiTest(width = 200, height = 200) {
            setContent(ComposeScene(role = "promo_card", cardShape = RoundedCornerShape(24.dp)))

            val image = onRoot().captureToImage()
            assertEquals(fill, centre(image))
            assertEquals(fill, corner(image))
        }

    @Test
    fun `a design system with no shape for the role keeps the rectangle`() =
        runDesktopComposeUiTest(width = 200, height = 200) {
            setContent(ComposeScene(role = "container", cardShape = null))

            val image = onRoot().captureToImage()
            assertEquals(fill, centre(image))
            assertEquals(fill, corner(image))
        }
}
