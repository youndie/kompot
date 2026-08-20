package io.github.youndie.kompot

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

// An open string key with not a single hardcoded "known" variant: kompot-core must not assume which
// set of colour slots a particular design system uses — Material3 is far from the only option.
// Ready-made constants for one system live in the opt-in :kompot-ds-material module (plain Kotlin,
// no Compose, so a headless server can use them too).
@Serializable
@JvmInline
value class ColorToken(
    val key: String,
) {
    companion object
}

// A typography token, on the same idea: an open string key rather than an enum, with no constants
// of any particular design system (see the ColorToken comment above).
@Serializable
@JvmInline
value class TypographyToken(
    val key: String,
) {
    companion object
}
