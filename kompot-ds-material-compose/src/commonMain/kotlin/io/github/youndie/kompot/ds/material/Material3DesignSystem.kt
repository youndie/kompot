package io.github.youndie.kompot.ds.material

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import io.github.youndie.kompot.KompotDesignSystem
import io.github.youndie.kompot.ColorToken
import io.github.youndie.kompot.TypographyToken
import io.github.youndie.kompot.material3.M3Colors
import io.github.youndie.kompot.material3.M3Typography

public class Material3DesignSystem : KompotDesignSystem {
    @Composable
    override fun resolveColor(token: ColorToken): Color =
        when (token) {
            M3Colors.Primary -> {
                MaterialTheme.colorScheme.primary
            }

            M3Colors.OnPrimary -> {
                MaterialTheme.colorScheme.onPrimary
            }

            M3Colors.PrimaryContainer -> {
                MaterialTheme.colorScheme.primaryContainer
            }

            M3Colors.OnPrimaryContainer -> {
                MaterialTheme.colorScheme.onPrimaryContainer
            }

            M3Colors.Secondary -> {
                MaterialTheme.colorScheme.secondary
            }

            M3Colors.OnSecondary -> {
                MaterialTheme.colorScheme.onSecondary
            }

            M3Colors.SecondaryContainer -> {
                MaterialTheme.colorScheme.secondaryContainer
            }

            M3Colors.OnSecondaryContainer -> {
                MaterialTheme.colorScheme.onSecondaryContainer
            }

            M3Colors.Surface -> {
                MaterialTheme.colorScheme.surface
            }

            M3Colors.OnSurface -> {
                MaterialTheme.colorScheme.onSurface
            }

            M3Colors.SurfaceVariant -> {
                MaterialTheme.colorScheme.surfaceVariant
            }

            M3Colors.OnSurfaceVariant -> {
                MaterialTheme.colorScheme.onSurfaceVariant
            }

            M3Colors.Background -> {
                MaterialTheme.colorScheme.background
            }

            M3Colors.OnBackground -> {
                MaterialTheme.colorScheme.onBackground
            }

            M3Colors.Error -> {
                MaterialTheme.colorScheme.error
            }

            M3Colors.OnError -> {
                MaterialTheme.colorScheme.onError
            }

            M3Colors.ErrorContainer -> {
                MaterialTheme.colorScheme.errorContainer
            }

            M3Colors.OnErrorContainer -> {
                MaterialTheme.colorScheme.onErrorContainer
            }

            M3Colors.Outline -> {
                MaterialTheme.colorScheme.outline
            }

            M3Colors.OutlineVariant -> {
                MaterialTheme.colorScheme.outlineVariant
            }

            else -> {
                warnUnknownToken("ColorToken", token.key)
                MaterialTheme.colorScheme.error
            }
        }

    @Composable
    override fun resolveTypography(token: TypographyToken): TextStyle =
        when (token) {
            M3Typography.DisplayLarge -> {
                MaterialTheme.typography.displayLarge
            }

            M3Typography.DisplayMedium -> {
                MaterialTheme.typography.displayMedium
            }

            M3Typography.DisplaySmall -> {
                MaterialTheme.typography.displaySmall
            }

            M3Typography.HeadlineLarge -> {
                MaterialTheme.typography.headlineLarge
            }

            M3Typography.HeadlineMedium -> {
                MaterialTheme.typography.headlineMedium
            }

            M3Typography.HeadlineSmall -> {
                MaterialTheme.typography.headlineSmall
            }

            M3Typography.TitleLarge -> {
                MaterialTheme.typography.titleLarge
            }

            M3Typography.TitleMedium -> {
                MaterialTheme.typography.titleMedium
            }

            M3Typography.TitleSmall -> {
                MaterialTheme.typography.titleSmall
            }

            M3Typography.BodyLarge -> {
                MaterialTheme.typography.bodyLarge
            }

            M3Typography.BodyMedium -> {
                MaterialTheme.typography.bodyMedium
            }

            M3Typography.BodySmall -> {
                MaterialTheme.typography.bodySmall
            }

            M3Typography.LabelLarge -> {
                MaterialTheme.typography.labelLarge
            }

            M3Typography.LabelMedium -> {
                MaterialTheme.typography.labelMedium
            }

            M3Typography.LabelSmall -> {
                MaterialTheme.typography.labelSmall
            }

            else -> {
                warnUnknownToken("TypographyToken", token.key)
                MaterialTheme.typography.bodyMedium
            }
        }
}

private fun warnUnknownToken(
    tokenType: String,
    key: String,
) {
    println("[KompotDesignSystem] Unknown $tokenType: \"$key\" — falling back. Check the backend.")
}
