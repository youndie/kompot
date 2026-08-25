package io.github.youndie.kompot

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import kotlin.jvm.JvmInline

// What a renderer draws for ITSELF — the shape of a button, the fill behind a field, whether a border
// is there at all — used to be outside the design system entirely. It answered colour and typography,
// both keyed by a name the SERVER sent, so a deployment could influence only what it named on the
// wire. A Material button rounds to a pill through ButtonDefaults.shape, which is not a value the
// theme supplies: setting every slot of Shapes to a zero radius changes nothing, and the only way out
// was replacing the renderer.
//
// A role is a client-side key and never travels: the server still names nothing about appearance,
// which is the property worth protecting. That is why this lives in :kompot-client rather than beside
// ColorToken in :kompot-core.
@JvmInline
value class SurfaceRole(
    val key: String,
)

object KompotSurfaceRoles {
    val Button = SurfaceRole("button")
    val Field = SurfaceRole("field")

    // Its own role rather than the field's, because the whole point of read_only_field is to say
    // "this is a value, not an input" — and drawn as a disabled input it says the opposite. What it
    // should look like instead is the deployment's to decide, not the toolkit's.
    val ReadOnlyField = SurfaceRole("read_only_field")
    val Container = SurfaceRole("container")

    // A button's emphasis is content, not theme: which one is primary is decided by whoever wrote the
    // screen. The renderer composes the role from the variant the server sent, so a design system
    // answers "button.quiet" the way it answers "button".
    fun button(variant: String?): SurfaceRole = if (variant == null) Button else SurfaceRole("${Button.key}.$variant")

    // The same composition for a boolean's affordance: a design system answers "checkbox_input.switch"
    // the way it answers "checkbox_input", and neither string is the protocol's business.
    val CheckboxInput = SurfaceRole("checkbox_input")

    fun checkboxInput(variant: String?): SurfaceRole =
        if (variant == null) CheckboxInput else SurfaceRole("${CheckboxInput.key}.$variant")
}

// Four slots, each with an explicit "not set". Unspecified means the toolkit's own default for that
// role; Color.Transparent on `outline` is how a design that forbids borders says so, without a flag
// that would have to mean the same thing.
//
// `content` is here because the first golden of this feature showed why: a container set without one
// leaves Material's own foreground on top of it, and a pale container turned "Cancel" into text you
// cannot read. A container that could be set alone would be a trap laid for whoever sets it.
data class KompotSurface(
    val shape: Shape? = null,
    val container: Color = Color.Unspecified,
    val content: Color = Color.Unspecified,
    val outline: Color = Color.Unspecified,
    // The words ON the control, which were outside the design system for the same reason its corner
    // was: a renderer that draws its own label picks a style nobody it was given can set. Material's
    // default typography names no font family, so the label came out in whatever the machine had
    // installed — invisible until two machines drew the same screen and disagreed about it.
    //
    // Here rather than through resolveTypography(token) because the caller is a ROLE, not a token: the
    // button asks for "the button's surface" and gets everything about it at once, variant included.
    // null keeps the ambient LocalTextStyle, which is what the control provided before.
    val textStyle: TextStyle? = null,
)
