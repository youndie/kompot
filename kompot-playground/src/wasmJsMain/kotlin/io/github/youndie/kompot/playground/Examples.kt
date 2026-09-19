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
            "style": "headline_small",
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
            "style": "headline_small",
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

internal val EXAMPLES: List<Example> =
    listOf(
        Example("A component an older client cannot read", SAMPLE_BODY),
        Example("A form: schema and screen in one body", FORM_BODY),
        Example("A screen that names its update channel", LIVE_SCREEN_BODY),
    )
