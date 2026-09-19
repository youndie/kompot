package io.github.youndie.kompot.playground

// A body, as an endpoint would answer it — a string rather than a Kotlin tree, and that is the same
// care KompotPreview takes for itself: an object and the bytes on the wire can disagree, and a page
// that built its screen from objects would be showing something no client will ever receive.
//
// In the source rather than in a resource because wasm has no file system and Compose resources are a
// dependency with a build step; B-30 turns this into a list to choose from, and that is the moment
// the question of where they live is worth asking.
internal val SAMPLE_BODY: String =
    """
    {
      "type": "column",
      "id": "root",
      "spacing": 12,
      "modifiers": [
        { "type": "padding", "all": 24 }
      ],
      "children": [
        {
          "type": "text",
          "id": "title",
          "text": "This screen is JSON, drawn by the client's own renderers",
          "style": "headline_small",
          "color": "on_surface"
        },
        {
          "type": "text",
          "id": "subtitle",
          "text": "Nothing here is a picture: the page decodes a server's body with the same serializers a client uses and draws it with the same Compose renderers.",
          "style": "body_medium",
          "color": "on_surface_variant"
        },
        {
          "type": "row",
          "id": "actions",
          "spacing": 8,
          "children": [
            {
              "type": "button",
              "id": "primary",
              "text": "A button the server asked for",
              "action": { "type": "close" }
            }
          ]
        }
      ]
    }
    """.trimIndent()
