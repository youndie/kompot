package io.github.youndie.kompot.studio.export

import io.github.youndie.kompot.ColorToken
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.TypographyToken
import io.github.youndie.kompot.standard.CloseAction
import io.github.youndie.kompot.standard.ColumnBuilder
import io.github.youndie.kompot.standard.NavigateAction
import io.github.youndie.kompot.standard.TextSpan
import io.github.youndie.kompot.standard.button
import io.github.youndie.kompot.standard.column
import io.github.youndie.kompot.standard.row
import io.github.youndie.kompot.standard.table
import io.github.youndie.kompot.standard.text

// Drafted from a JSON body by kompot-studio. Names marked TODO are guesses: the
// schema carries wire types, and what a class is called in Kotlin is not on the wire.
public fun witnessScreenDraft(): KompotComponent =
    ColumnBuilder(id = "witness").apply {
        spacing(8)
        modifier {
            padding(top = 4, bottom = 0, start = 0, end = 0)
        }
        alignment("center")
        arrangement("space_between")
        action(NavigateAction(deeplink = "app://witness"))
        accessibilityLabel("The whole screen")
        text("Total: 12", style = TypographyToken("title_medium"), color = ColorToken("on_surface"), heading = true, maxLines = 2, ellipsis = false, spans = listOf(TextSpan(text = "Total: "), TextSpan(text = "12", style = TypographyToken("label_large"), color = ColorToken("error"), action = CloseAction)), modifierBlock = {
            padding(top = 0, bottom = 0, start = 8, end = 0)
        })
        button("Pay", CloseAction, id = "pay", variant = "primary", accessibilityLabel = "Pay twelve", modifierBlock = {
            padding(top = 0, bottom = 0, start = 0, end = 8)
        })
        row(id = "rail") {
            spacing(4)
            modifier {
                padding(top = 0, bottom = 2, start = 0, end = 0)
            }
            alignment("end")
            arrangement("space_evenly")
            scrollable()
            action(NavigateAction(deeplink = "app://rail"))
            accessibilityLabel("Open the rail")
            text("In the rail")
            text("Named like a path that is not its own", id = "root/2/1")
        }
        column {
            spacing(2)
            modifier {
                padding(top = 2, bottom = 0, start = 0, end = 0)
            }
            alignment("start")
            arrangement("center")
            action(CloseAction)
            accessibilityLabel("Open the card")
            text("In the card")
        }
        table(id = "table", modifierBlock = {
            padding(top = 2, bottom = 0, start = 0, end = 0)
        }) {
            row("Name", "Value", header = true)
            row("a", "b")
        }
    }.build()
