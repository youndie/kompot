package io.github.youndie.kompot.studio.export

import io.github.youndie.kompot.ColorToken
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.TypographyToken
import io.github.youndie.kompot.standard.CloseAction
import io.github.youndie.kompot.standard.button
import io.github.youndie.kompot.standard.kompotScreen
import io.github.youndie.kompot.standard.text

// Drafted from a JSON body by kompot-studio. Names marked TODO are guesses: the
// schema carries wire types, and what a class is called in Kotlin is not on the wire.
public fun sampleScreenDraft(): KompotComponent =
    kompotScreen {
        spacing(12)
        modifier {
            padding(top = 16, bottom = 16, start = 16, end = 16)
        }
        text("The body is the source of truth", style = TypographyToken("headline_small"), color = ColorToken("on_surface"), id = "title")
        text("Edit the JSON on the left. The frame on the right is drawn by the same renderers a client ships.", style = TypographyToken("body_medium"), color = ColorToken("on_surface_variant"), id = "subtitle")
        button("A Material button", CloseAction, id = "cta")
    }
