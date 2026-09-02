package io.github.youndie.kompot.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import io.github.youndie.kompot.KompotActionHandler
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotDegradationKind
import io.github.youndie.kompot.KompotDegradationSink
import io.github.youndie.kompot.KompotDesignSystem
import io.github.youndie.kompot.KompotRegistry
import io.github.youndie.kompot.LocalKompotDegradationSink
import io.github.youndie.kompot.LocalKompotDesignSystem
import io.github.youndie.kompot.LocalKompotRegistry
import io.github.youndie.kompot.form.FieldValue
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.form.FormSchema
import io.github.youndie.kompot.forms.KompotFormResponse
import io.github.youndie.kompot.kompotJson
import io.github.youndie.kompot.realtime.KompotScreenResponse
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

// The state a screen is in, which is the thing a picture of a form leaves out. "The checkout form"
// is not one image: empty, filled, and showing every validation error are three, and the difference
// between them is the point of looking.
public class KompotPreviewState(
    // What the fields hold. The same map FormController takes, because it is the same thing: a draft
    // the person had already typed, or the values a server prefilled.
    public val values: Map<String, FieldValue> = emptyMap(),
    // Whether every field counts as touched. Errors are shown on a field the person has left, so an
    // untouched form is valid-looking however empty it is — and a preview of "what the form says when
    // somebody submits it blank" needs this rather than a fake submit.
    public val allFieldsChanged: Boolean = false,
)

// A screen, drawn from the body an endpoint returns.
//
// The input is the BODY and not a component the caller has in hand, and that is the whole point
// rather than an inconvenience. An object and the bytes on the wire can disagree: `call.respond`
// resolves the serialiser from the concrete runtime class and drops the "type" discriminator on the
// ROOT of a tree while every nested child keeps its own, so a screen that renders perfectly from the
// object in memory is a screen the client cannot decode. A preview taken from the object would be a
// picture of a bug's absence.
//
// For the same reason this module ships no helper that turns a component into a body. One would be
// two lines, everybody would use it, and it would round-trip ITS OWN correct encoding rather than the
// one the server produces — the round trip would look like a check and check nothing. A caller who
// has an object encodes it the way their server does, which is exactly the step worth not hiding.
//
// A body arrives in one of three shapes, and they are told apart by what they carry rather than by a
// flag: a form response has a schema, a screen response has a screen and no schema, and anything else
// is a bare component tree.
@Composable
public fun KompotPreview(
    body: String,
    // The application's, both of them: the toolkit can draw the components it knows, and a deployment
    // previewing its own screens is previewing its own components. A registry assembled here would be
    // a picture of a different product.
    registry: KompotRegistry,
    designSystem: KompotDesignSystem,
    state: KompotPreviewState = KompotPreviewState(),
    // THE SAME Json the client decodes with, and the default is only the toolkit's own types.
    //
    // Not a detail: a preview that can decode more than its client can is a picture of a screen the
    // client cannot show, and a preview that can decode less fails on a body that works. The engine's
    // module does not carry form-standard's field definitions, for instance — an application adds
    // them, and a preview that quietly added them for you would decode a schema the application's own
    // client refuses.
    json: Json = kompotJson(),
    // A preview is not interactive: a tap has nowhere to go and a screenshot cannot make one. The
    // parameter exists for the case where a renderer asks the handler something at composition time.
    actionHandler: KompotActionHandler = KompotActionHandler {},
    // LOUD by default, and deliberately. A missing renderer degrades to a grey placeholder on a real
    // screen, which is the right behaviour there and the wrong one here: recorded into a golden it
    // becomes the expected appearance of the screen, and the check goes on passing for as long as the
    // component stays missing. A preview that cannot draw the screen should say so, not photograph
    // the hole.
    onDegraded: (kind: KompotDegradationKind, originalType: String) -> Unit = ::failOnDegradation,
) {
    val decoded = remember(body, json) { decodeBody(json, body) }
    val controller =
        remember(decoded, state) {
            FormController(decoded.schema, initialValues = state.values)
                .also { if (state.allFieldsChanged) it.markAllAsChanged() }
        }

    CompositionLocalProvider(
        LocalKompotDesignSystem provides designSystem,
        LocalKompotRegistry provides registry,
        LocalKompotDegradationSink provides KompotDegradationSink { kind, originalType, _ -> onDegraded(kind, originalType) },
    ) {
        registry.RenderNode(decoded.screen, actionHandler, controller)
    }
}

private fun failOnDegradation(
    kind: KompotDegradationKind,
    originalType: String,
): Nothing =
    error(
        "$kind: \"$originalType\". A preview draws with the registry and the serializers module it was given, " +
            "so this is a renderer or a registration missing from THEM, not a screen degrading in the field." +
            if (originalType == NO_DISCRIMINATOR) NO_DISCRIMINATOR_HINT else "",
    )

// The type a node decodes to when the body named none at all: the open hierarchy's default
// deserializer is handed a null class name and passes this through. It is worth telling apart from an
// unregistered type, because the cause is different and specific — and because it is the mistake this
// module exists to catch, so a preview that merely says "unknown" would be sending the reader looking
// for a missing registration that is not missing.
private const val NO_DISCRIMINATOR = "unknown"

private const val NO_DISCRIMINATOR_HINT =
    " The body carried no \"type\" on that node, which is what a CONCRETE serialiser writes for the root " +
        "it is handed — call.respond(component), or encodeToString(MyComponent.serializer(), ...). Encode the " +
        "root polymorphically and the discriminator comes back."

private class DecodedBody(
    val screen: KompotComponent,
    val schema: FormSchema,
)

// A screen that is not a form still needs a controller, because every renderer takes one — so it gets
// a schema with no fields in it rather than a nullable controller threaded through the renderers.
private val NO_FIELDS = FormSchema(formId = "preview", fields = emptyList())

private fun decodeBody(
    json: Json,
    body: String,
): DecodedBody {
    val root: JsonObject = json.parseToJsonElement(body).jsonObject

    return when {
        "schema" in root -> {
            val response = json.decodeFromString(KompotFormResponse.serializer(), body)
            DecodedBody(response.screen, response.schema)
        }

        "screen" in root -> {
            val response = json.decodeFromString(KompotScreenResponse.serializer(), body)
            DecodedBody(response.screen, NO_FIELDS)
        }

        // PolymorphicSerializer rather than KompotComponent.serializer(): the hierarchy is OPEN, so the
        // interface has no generated serializer of its own — the same asymmetry that makes a plain
        // call.respond drop the discriminator on a root, met here from the reading side.
        else -> DecodedBody(json.decodeFromString(PolymorphicSerializer(KompotComponent::class), body), NO_FIELDS)
    }
}
