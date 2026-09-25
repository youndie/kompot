package io.github.youndie.kompot.playground

// The bodies the page opens with, and the reason it opens with anything at all: an empty text box is
// an assignment, not a showcase. Somebody who arrived by accident closes the tab before writing their
// first JSON.
//
// The first one is the degradation story, because that is the one thing here nobody else's server-driven
// toolkit will show them.
//
// They live in these sources rather than being pulled from kompot-client-tck/corpus: that corpus holds
// pairs of "input → observable outcome" for a CLIENT (§17), not bodies of screens, and mixing the two
// is what the specification warns about in a paragraph of its own.
internal data class Example(
    val name: String,
    val body: String,
)

// The second of the three shapes a body arrives in (SPEC.md §16.1): a form carries its SCHEMA beside
// the screen, and the client builds its controller from that rather than from the widgets. Worth a
// place here because it is the shape a reader is least likely to guess — and because the page's own
// claim is that it decodes what a real endpoint answers, all three envelopes included.
private val FORM_BODY: String =
    """
    {
      "schema": {
        "formId": "signup",
        "fields": [
          {
            "type": "text_field",
            "fieldId": "email",
            "rules": [ { "type": "required", "errorMessage": "An address is needed to write back" } ]
          }
        ]
      },
      "screen": {
        "type": "column",
        "id": "root",
        "spacing": 12,
        "modifiers": [ { "type": "padding", "all": 24 } ],
        "children": [
          {
            "type": "text",
            "id": "title",
            "text": "A form is a schema and a screen in one body",
            "style": "headline_small", "heading": true,
            "color": "on_surface"
          },
          {
            "type": "text",
            "id": "note",
            "text": "The rules live in the schema, not in the widgets: the same field can be drawn by any client that knows text_input.",
            "style": "body_medium",
            "color": "on_surface_variant"
          },
          { "type": "text_input", "id": "email_input", "fieldId": "email", "label": "Email" },
          {
            "type": "button",
            "id": "submit",
            "text": "Submit",
            "action": { "type": "submit_form", "formId": "signup" }
          }
        ]
      }
    }
    """.trimIndent()

// The third shape: a screen that is not a form and still wants live updates, so it travels in the
// envelope that carries the topic (§10.4). The page draws the screen; the topic is what a real client
// would subscribe with, and it is visible in the tree as the body's own property.
private val LIVE_SCREEN_BODY: String =
    """
    {
      "realtimeTopic": "orders:user1",
      "screen": {
        "type": "column",
        "id": "root",
        "spacing": 12,
        "modifiers": [ { "type": "padding", "all": 24 } ],
        "children": [
          {
            "type": "text",
            "id": "title",
            "text": "A screen that names its update channel",
            "style": "headline_small", "heading": true,
            "color": "on_surface"
          },
          {
            "type": "text",
            "id": "note",
            "text": "realtimeTopic rides on the envelope rather than inside the tree: the client hands it back as it is and never reads it. Personal data means a personal topic, and that is what keeps one subscriber's updates away from another.",
            "style": "body_medium",
            "color": "on_surface_variant"
          }
        ]
      }
    }
    """.trimIndent()

// Layers (SPEC.md §4.8). The picture sets the box's size; the badge and the caption are boxes that FILL
// it and align their own child — which is how one layer sits in a corner and another at the bottom
// without an alignment per child on the wire.
private val LAYERS_BODY: String =
    """
    {
      "type": "column",
      "id": "root",
      "spacing": 12,
      "modifiers": [ { "type": "padding", "all": 24 } ],
      "children": [
        {
          "type": "text",
          "id": "title",
          "text": "One node laid over another",
          "style": "headline_small", "heading": true,
          "color": "on_surface"
        },
        {
          "type": "box",
          "id": "card",
          "children": [
            {
              "type": "column",
              "id": "picture",
              "modifiers": [
                { "type": "size", "widthDp": 320, "heightDp": 160 },
                { "type": "background", "color": "primary_container" }
              ],
              "children": []
            },
            {
              "type": "box",
              "id": "badge_frame",
              "alignment": "top_end",
              "modifiers": [ { "type": "size", "width": "Fill", "height": "Fill" }, { "type": "padding", "all": 8 } ],
              "children": [ { "type": "text", "id": "badge", "text": "NEW", "style": "label_large", "color": "on_primary_container" } ]
            },
            {
              "type": "box",
              "id": "caption_frame",
              "alignment": "bottom_start",
              "modifiers": [ { "type": "size", "width": "Fill", "height": "Fill" }, { "type": "padding", "all": 12 } ],
              "children": [ { "type": "text", "id": "caption", "text": "A caption over the picture", "style": "title_medium", "color": "on_primary_container" } ]
            }
          ]
        },
        {
          "type": "text",
          "id": "note",
          "text": "The badge and the caption are boxes that fill the picture and place their own child. A client older than box draws the equivalent the server names in its place.",
          "style": "body_medium",
          "color": "on_surface_variant"
        }
      ]
    }
    """.trimIndent()

// A rail of cards (SPEC.md §4.9): a row that keeps the page's width while its content scrolls sideways.
private val RAIL_BODY: String =
    """
    {
      "type": "column",
      "id": "root",
      "spacing": 16,
      "modifiers": [ { "type": "padding", "all": 24 } ],
      "children": [
        { "type": "text", "id": "title", "text": "A rail of cards", "style": "headline_small", "heading": true, "color": "on_surface" },
        {
          "type": "row",
          "id": "rail",
          "spacing": 12,
          "scrollable": true,
          "modifiers": [ { "type": "size", "width": "Fill" } ],
          "children": [
            { "type": "text", "id": "card_1", "text": "Card 1", "style": "title_medium", "color": "on_primary_container", "modifiers": [ { "type": "size", "widthDp": 140, "heightDp": 96 }, { "type": "background", "color": "primary_container" }, { "type": "padding", "all": 12 } ] },
            { "type": "text", "id": "card_2", "text": "Card 2", "style": "title_medium", "color": "on_primary_container", "modifiers": [ { "type": "size", "widthDp": 140, "heightDp": 96 }, { "type": "background", "color": "primary_container" }, { "type": "padding", "all": 12 } ] },
            { "type": "text", "id": "card_3", "text": "Card 3", "style": "title_medium", "color": "on_primary_container", "modifiers": [ { "type": "size", "widthDp": 140, "heightDp": 96 }, { "type": "background", "color": "primary_container" }, { "type": "padding", "all": 12 } ] },
            { "type": "text", "id": "card_4", "text": "Card 4", "style": "title_medium", "color": "on_primary_container", "modifiers": [ { "type": "size", "widthDp": 140, "heightDp": 96 }, { "type": "background", "color": "primary_container" }, { "type": "padding", "all": 12 } ] },
            { "type": "text", "id": "card_5", "text": "Card 5", "style": "title_medium", "color": "on_primary_container", "modifiers": [ { "type": "size", "widthDp": 140, "heightDp": 96 }, { "type": "background", "color": "primary_container" }, { "type": "padding", "all": 12 } ] },
            { "type": "text", "id": "card_6", "text": "Card 6", "style": "title_medium", "color": "on_primary_container", "modifiers": [ { "type": "size", "widthDp": 140, "heightDp": 96 }, { "type": "background", "color": "primary_container" }, { "type": "padding", "all": 12 } ] },
            { "type": "text", "id": "card_7", "text": "Card 7", "style": "title_medium", "color": "on_primary_container", "modifiers": [ { "type": "size", "widthDp": 140, "heightDp": 96 }, { "type": "background", "color": "primary_container" }, { "type": "padding", "all": 12 } ] },
            { "type": "text", "id": "card_8", "text": "Card 8", "style": "title_medium", "color": "on_primary_container", "modifiers": [ { "type": "size", "widthDp": 140, "heightDp": 96 }, { "type": "background", "color": "primary_container" }, { "type": "padding", "all": 12 } ] }
          ]
        },
        { "type": "text", "id": "p1", "text": "The row keeps the width of the page; its content is as wide as it is and slides under a sideways gesture — a trackpad swipe, shift and the wheel, or a finger.", "style": "body_medium", "color": "on_surface_variant" },
        { "type": "text", "id": "p2", "text": "The wheel turned over the rail still moves the page: a rail takes only the sideways gesture.", "style": "body_medium", "color": "on_surface_variant" },
        { "type": "text", "id": "p3", "text": "A client older than the field ignores it and draws the row it always drew: the cards that fit, and nothing past the edge.", "style": "body_medium", "color": "on_surface_variant" },
        { "type": "text", "id": "p4", "text": "In a row that scrolls, weight is ignored — a share of an unbounded width is nothing.", "style": "body_medium", "color": "on_surface_variant" },
        { "type": "text", "id": "p5", "text": "These paragraphs are here so the page is taller than the window, which is what makes the second sentence above something to try.", "style": "body_medium", "color": "on_surface_variant" },
        { "type": "text", "id": "p6", "text": "One more, for the same reason.", "style": "body_medium", "color": "on_surface_variant" }
      ]
    }
    """.trimIndent()

// show_message (SPEC.md §16.4): an answer in words, drawn by the client's design system. The second
// button's message carries a button of its own, and what that one raises is another message — the
// message's button goes through the same chain as every other action.
private val MESSAGES_BODY: String =
    """
    {
      "type": "column",
      "id": "root",
      "spacing": 12,
      "modifiers": [ { "type": "padding", "all": 24 } ],
      "children": [
        { "type": "text", "id": "title", "text": "An answer in words", "style": "headline_small", "heading": true, "color": "on_surface" },
        {
          "type": "text",
          "id": "note",
          "text": "A perform usually answers with a message rather than a new screen. Here the buttons raise the answer themselves, since the page has no server.",
          "style": "body_medium",
          "color": "on_surface_variant"
        },
        {
          "type": "button",
          "id": "copy",
          "text": "Copy the link",
          "action": { "type": "show_message", "text": "Link copied" }
        },
        {
          "type": "button",
          "id": "archive",
          "text": "Archive the card",
          "action": {
            "type": "show_message",
            "text": "Card archived",
            "actionLabel": "Undo",
            "action": { "type": "show_message", "text": "Card restored" }
          }
        }
      ]
    }
    """.trimIndent()

// divider and spacer (SPEC.md §4.10): a rule across the axis, room along it. Settings-like rows split
// by dividers; in each row a weighted spacer pushes the value to the far edge, and the rows' own
// divider is vertical because a row is horizontal.
private val RULES_BODY: String =
    """
    {
      "type": "column",
      "id": "root",
      "spacing": 12,
      "modifiers": [ { "type": "padding", "all": 24 } ],
      "children": [
        { "type": "text", "id": "title", "text": "Rules and room", "style": "headline_small", "heading": true, "color": "on_surface" },
        {
          "type": "row", "id": "language",
          "action": { "type": "show_message", "text": "The language picker would open here" },
          "accessibilityLabel": "Language: English. Change",
          "children": [
            { "type": "text", "id": "language_label", "text": "Language", "style": "body_large", "color": "on_surface" },
            { "type": "spacer", "id": "language_gap", "modifiers": [ { "type": "weight", "value": 1.0 } ] },
            { "type": "text", "id": "language_value", "text": "English", "style": "body_large", "color": "on_surface_variant" }
          ]
        },
        { "type": "divider", "id": "rule_1" },
        {
          "type": "row", "id": "theme",
          "children": [
            { "type": "text", "id": "theme_label", "text": "Theme", "style": "body_large", "color": "on_surface" },
            { "type": "spacer", "id": "theme_gap", "modifiers": [ { "type": "weight", "value": 1.0 } ] },
            { "type": "text", "id": "theme_light", "text": "Light", "style": "body_large", "color": "on_surface_variant" },
            { "type": "spacer", "id": "theme_room", "size": 12 },
            { "type": "divider", "id": "theme_rule" },
            { "type": "spacer", "id": "theme_room_2", "size": 12 },
            { "type": "text", "id": "theme_dark", "text": "Dark", "style": "body_large", "color": "on_surface_variant" }
          ]
        },
        { "type": "divider", "id": "rule_2" },
        { "type": "spacer", "id": "before_note", "size": 24 },
        {
          "type": "text", "id": "note",
          "text": "The rules between the rows are horizontal and the one inside the theme row is vertical: a divider runs across the stack it is in. The values sit at the right because a spacer with weight takes the room between.",
          "style": "body_medium", "color": "on_surface_variant"
        }
      ]
    }
    """.trimIndent()

// present and confirm (SPEC.md §12.5): a question before a destructive action, and a tree over the
// screen that closes itself. What the agreed action and the sheet's buttons raise is a message, since
// the page has no server to send anything to.
private val OVERLAYS_BODY: String =
    """
    {
      "type": "column",
      "id": "root",
      "spacing": 12,
      "modifiers": [ { "type": "padding", "all": 24 } ],
      "children": [
        { "type": "text", "id": "title", "text": "Over the screen", "style": "headline_small", "heading": true, "color": "on_surface" },
        {
          "type": "text", "id": "note",
          "text": "confirm wraps the action it guards, so a client that does not know it runs nothing rather than deleting on the first tap. present shows a tree over the screen; close inside it closes it.",
          "style": "body_medium", "color": "on_surface_variant"
        },
        {
          "type": "button", "id": "delete", "text": "Delete the board",
          "action": {
            "type": "confirm",
            "question": "Delete the board?",
            "detail": "Its cards go with it, and this cannot be undone.",
            "confirmLabel": "Delete",
            "cancelLabel": "Keep it",
            "action": { "type": "show_message", "text": "Board deleted" }
          }
        },
        {
          "type": "button", "id": "move", "text": "Move the card",
          "action": {
            "type": "present",
            "kind": "sheet",
            "content": {
              "type": "column", "id": "move_sheet", "spacing": 8,
              "modifiers": [ { "type": "padding", "all": 24 } ],
              "children": [
                { "type": "text", "id": "move_title", "text": "Move to", "style": "title_medium", "color": "on_surface" },
                { "type": "button", "id": "to_doing", "text": "Doing", "action": { "type": "show_message", "text": "Moved to Doing" } },
                { "type": "button", "id": "to_done", "text": "Done", "action": { "type": "show_message", "text": "Moved to Done" } },
                { "type": "button", "id": "move_close", "text": "Close", "variant": "text", "action": { "type": "close" } }
              ]
            }
          }
        }
      ]
    }
    """.trimIndent()

internal val EXAMPLES: List<Example> =
    listOf(
        Example("A component an older client cannot read", SAMPLE_BODY),
        Example("A form: schema and screen in one body", FORM_BODY),
        Example("A screen that names its update channel", LIVE_SCREEN_BODY),
        Example("Layers: a badge and a caption over a picture", LAYERS_BODY),
        Example("A rail of cards that scrolls sideways", RAIL_BODY),
        Example("An answer in words: show_message", MESSAGES_BODY),
        Example("Rules and room: divider and spacer", RULES_BODY),
        Example("Over the screen: confirm and present", OVERLAYS_BODY),
    )
