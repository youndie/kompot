package io.github.youndie.kompot.material3

import io.github.youndie.kompot.ColorToken
import io.github.youndie.kompot.TypographyToken

public object M3Colors {
    public val Primary: ColorToken = ColorToken("primary")
    public val OnPrimary: ColorToken = ColorToken("on_primary")
    public val PrimaryContainer: ColorToken = ColorToken("primary_container")
    public val OnPrimaryContainer: ColorToken = ColorToken("on_primary_container")

    public val Secondary: ColorToken = ColorToken("secondary")
    public val OnSecondary: ColorToken = ColorToken("on_secondary")
    public val SecondaryContainer: ColorToken = ColorToken("secondary_container")
    public val OnSecondaryContainer: ColorToken = ColorToken("on_secondary_container")

    public val Surface: ColorToken = ColorToken("surface")
    public val OnSurface: ColorToken = ColorToken("on_surface")
    public val SurfaceVariant: ColorToken = ColorToken("surface_variant")
    public val OnSurfaceVariant: ColorToken = ColorToken("on_surface_variant")

    public val Background: ColorToken = ColorToken("background")
    public val OnBackground: ColorToken = ColorToken("on_background")

    public val Error: ColorToken = ColorToken("error")
    public val OnError: ColorToken = ColorToken("on_error")
    public val ErrorContainer: ColorToken = ColorToken("error_container")
    public val OnErrorContainer: ColorToken = ColorToken("on_error_container")

    public val Outline: ColorToken = ColorToken("outline")
    public val OutlineVariant: ColorToken = ColorToken("outline_variant")

    // Handy as one list: a backend can validate a theme's keys against it.
    public val all: List<ColorToken> by lazy {
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

public object M3Typography {
    public val DisplayLarge: TypographyToken = TypographyToken("display_large")
    public val DisplayMedium: TypographyToken = TypographyToken("display_medium")
    public val DisplaySmall: TypographyToken = TypographyToken("display_small")

    public val HeadlineLarge: TypographyToken = TypographyToken("headline_large")
    public val HeadlineMedium: TypographyToken = TypographyToken("headline_medium")
    public val HeadlineSmall: TypographyToken = TypographyToken("headline_small")

    public val TitleLarge: TypographyToken = TypographyToken("title_large")
    public val TitleMedium: TypographyToken = TypographyToken("title_medium")
    public val TitleSmall: TypographyToken = TypographyToken("title_small")

    public val BodyLarge: TypographyToken = TypographyToken("body_large")
    public val BodyMedium: TypographyToken = TypographyToken("body_medium")
    public val BodySmall: TypographyToken = TypographyToken("body_small")

    public val LabelLarge: TypographyToken = TypographyToken("label_large")
    public val LabelMedium: TypographyToken = TypographyToken("label_medium")
    public val LabelSmall: TypographyToken = TypographyToken("label_small")

    public val all: List<TypographyToken> by lazy {
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
