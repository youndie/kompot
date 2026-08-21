package io.github.youndie.kompot.ds.material

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.text.TextStyle
import io.github.youndie.kompot.ColorToken
import io.github.youndie.kompot.TypographyToken
import io.github.youndie.kompot.material3.*
import kotlin.test.Test
import kotlin.test.assertEquals

private val colorCases: List<Pair<ColorToken, ColorScheme.() -> Color>> =
    listOf(
        M3Colors.Primary to { primary },
        M3Colors.OnPrimary to { onPrimary },
        M3Colors.PrimaryContainer to { primaryContainer },
        M3Colors.OnPrimaryContainer to { onPrimaryContainer },
        M3Colors.Secondary to { secondary },
        M3Colors.OnSecondary to { onSecondary },
        M3Colors.SecondaryContainer to { secondaryContainer },
        M3Colors.OnSecondaryContainer to { onSecondaryContainer },
        M3Colors.Surface to { surface },
        M3Colors.OnSurface to { onSurface },
        M3Colors.SurfaceVariant to { surfaceVariant },
        M3Colors.OnSurfaceVariant to { onSurfaceVariant },
        M3Colors.Background to { background },
        M3Colors.OnBackground to { onBackground },
        M3Colors.Error to { error },
        M3Colors.OnError to { onError },
        M3Colors.ErrorContainer to { errorContainer },
        M3Colors.OnErrorContainer to { onErrorContainer },
        M3Colors.Outline to { outline },
        M3Colors.OutlineVariant to { outlineVariant },
    )

private val typographyCases: List<Pair<TypographyToken, androidx.compose.material3.Typography.() -> TextStyle>> =
    listOf(
        M3Typography.DisplayLarge to { displayLarge },
        M3Typography.DisplayMedium to { displayMedium },
        M3Typography.DisplaySmall to { displaySmall },
        M3Typography.HeadlineLarge to { headlineLarge },
        M3Typography.HeadlineMedium to { headlineMedium },
        M3Typography.HeadlineSmall to { headlineSmall },
        M3Typography.TitleLarge to { titleLarge },
        M3Typography.TitleMedium to { titleMedium },
        M3Typography.TitleSmall to { titleSmall },
        M3Typography.BodyLarge to { bodyLarge },
        M3Typography.BodyMedium to { bodyMedium },
        M3Typography.BodySmall to { bodySmall },
        M3Typography.LabelLarge to { labelLarge },
        M3Typography.LabelMedium to { labelMedium },
        M3Typography.LabelSmall to { labelSmall },
    )

@OptIn(ExperimentalTestApi::class)
class Material3DesignSystemTest {
    @Test
    fun `every known color token resolves to the matching Material3 color scheme slot`() =
        runDesktopComposeUiTest {
            val designSystem = Material3DesignSystem()
            var scheme: ColorScheme? = null
            var resolved: Map<ColorToken, Color> = emptyMap()

            setContent {
                MaterialTheme {
                    scheme = MaterialTheme.colorScheme
                    resolved = colorCases.associate { (token, _) -> token to designSystem.resolveColor(token) }
                }
            }

            waitForIdle()
            val currentScheme = requireNotNull(scheme)
            colorCases.forEach { (token, selector) ->
                assertEquals(currentScheme.selector(), resolved.getValue(token), "mismatch for $token")
            }
        }

    @Test
    fun `an unknown color token falls back to the error color instead of crashing`() =
        runDesktopComposeUiTest {
            val designSystem = Material3DesignSystem()
            var scheme: ColorScheme? = null
            var resolved: Color? = null

            setContent {
                MaterialTheme {
                    scheme = MaterialTheme.colorScheme
                    resolved = designSystem.resolveColor(ColorToken("brand_new_unreleased_token"))
                }
            }

            waitForIdle()
            assertEquals(requireNotNull(scheme).error, resolved)
        }

    @Test
    fun `every known typography token resolves to the matching Material3 typography slot`() =
        runDesktopComposeUiTest {
            val designSystem = Material3DesignSystem()
            var typography: androidx.compose.material3.Typography? = null
            var resolved: Map<TypographyToken, TextStyle> = emptyMap()

            setContent {
                MaterialTheme {
                    typography = MaterialTheme.typography
                    resolved = typographyCases.associate { (token, _) -> token to designSystem.resolveTypography(token) }
                }
            }

            waitForIdle()
            val currentTypography = requireNotNull(typography)
            typographyCases.forEach { (token, selector) ->
                assertEquals(currentTypography.selector(), resolved.getValue(token), "mismatch for $token")
            }
        }

    @Test
    fun `an unknown typography token falls back to bodyMedium instead of crashing`() =
        runDesktopComposeUiTest {
            val designSystem = Material3DesignSystem()
            var typography: androidx.compose.material3.Typography? = null
            var resolved: TextStyle? = null

            setContent {
                MaterialTheme {
                    typography = MaterialTheme.typography
                    resolved = designSystem.resolveTypography(TypographyToken("brand_new_unreleased_token"))
                }
            }

            waitForIdle()
            assertEquals(requireNotNull(typography).bodyMedium, resolved)
        }
}
