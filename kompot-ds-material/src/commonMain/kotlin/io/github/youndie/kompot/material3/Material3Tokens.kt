package io.github.youndie.kompot.material3

import io.github.youndie.kompot.ColorToken
import io.github.youndie.kompot.TypographyToken

object M3Colors {
    val Primary = ColorToken("primary")
    val OnPrimary = ColorToken("on_primary")
    val PrimaryContainer = ColorToken("primary_container")
    val OnPrimaryContainer = ColorToken("on_primary_container")

    val Secondary = ColorToken("secondary")
    val OnSecondary = ColorToken("on_secondary")
    val SecondaryContainer = ColorToken("secondary_container")
    val OnSecondaryContainer = ColorToken("on_secondary_container")

    val Surface = ColorToken("surface")
    val OnSurface = ColorToken("on_surface")
    val SurfaceVariant = ColorToken("surface_variant")
    val OnSurfaceVariant = ColorToken("on_surface_variant")

    val Background = ColorToken("background")
    val OnBackground = ColorToken("on_background")

    val Error = ColorToken("error")
    val OnError = ColorToken("on_error")
    val ErrorContainer = ColorToken("error_container")
    val OnErrorContainer = ColorToken("on_error_container")

    val Outline = ColorToken("outline")
    val OutlineVariant = ColorToken("outline_variant")

    // Handy as one list: a backend can validate a theme's keys against it.
    val all: List<ColorToken> by lazy {
        listOf(
            Primary,
            OnPrimary,
            PrimaryContainer,
            OnPrimaryContainer,
            Secondary,
            OnSecondary,
            SecondaryContainer,
            OnSecondaryContainer,
            Surface,
            OnSurface,
            SurfaceVariant,
            OnSurfaceVariant,
            Background,
            OnBackground,
            Error,
            OnError,
            ErrorContainer,
            OnErrorContainer,
            Outline,
            OutlineVariant,
        )
    }
}

object M3Typography {
    val DisplayLarge = TypographyToken("display_large")
    val DisplayMedium = TypographyToken("display_medium")
    val DisplaySmall = TypographyToken("display_small")

    val HeadlineLarge = TypographyToken("headline_large")
    val HeadlineMedium = TypographyToken("headline_medium")
    val HeadlineSmall = TypographyToken("headline_small")

    val TitleLarge = TypographyToken("title_large")
    val TitleMedium = TypographyToken("title_medium")
    val TitleSmall = TypographyToken("title_small")

    val BodyLarge = TypographyToken("body_large")
    val BodyMedium = TypographyToken("body_medium")
    val BodySmall = TypographyToken("body_small")

    val LabelLarge = TypographyToken("label_large")
    val LabelMedium = TypographyToken("label_medium")
    val LabelSmall = TypographyToken("label_small")

    val all: List<TypographyToken> by lazy {
        listOf(
            DisplayLarge,
            DisplayMedium,
            DisplaySmall,
            HeadlineLarge,
            HeadlineMedium,
            HeadlineSmall,
            TitleLarge,
            TitleMedium,
            TitleSmall,
            BodyLarge,
            BodyMedium,
            BodySmall,
            LabelLarge,
            LabelMedium,
            LabelSmall,
        )
    }
}
