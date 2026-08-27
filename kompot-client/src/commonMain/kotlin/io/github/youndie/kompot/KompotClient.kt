package io.github.youndie.kompot

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import io.github.youndie.kompot.forms.kompotFormsSerializersModule
import io.github.youndie.kompot.generated.generatedFormsSerializersModule
import io.github.youndie.kompot.generated.generatedImagesSerializersModule
import io.github.youndie.kompot.generated.generatedStandardSerializersModule
import io.github.youndie.kompot.generated.generatedWizardSerializersModule
import io.github.youndie.kompot.standard.kompotStandardSerializersModule
import io.github.youndie.kompot.wizard.kompotWizardSerializersModule

    // The types the engine itself speaks: the core, with its fallback for an unknown type — without it
    // a newer backend would take an older client's whole screen down — plus the plug-ins the engine
    // draws. The generated modules are the component registrations KSP writes; the hand-written ones
    // register the actions that annotation does not cover.
//
    // Nothing of an application's own belongs here: which components an application has, it knows.
val kompotEngineSerializersModule: SerializersModule =
    kompotCoreSerializersModule +
        kompotStandardSerializersModule +
        generatedStandardSerializersModule +
        kompotFormsSerializersModule +
        generatedFormsSerializersModule +
        generatedImagesSerializersModule +
        generatedWizardSerializersModule +
        kompotWizardSerializersModule

// The engine's Json plus an application's types: its own components, field types and feature actions.
//
// This used to be a ready-made constant with one application's component set written into it — the
// library knowing what a particular product contains, which is exactly what open hierarchies exist to
// avoid. The application already assembled its own Json on top; it was simply on top of an already
// polluted one.
fun kompotJson(applicationModule: SerializersModule = EmptySerializersModule()): Json =
    Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"

        serializersModule = kompotEngineSerializersModule + applicationModule
    }

fun interface KompotActionHandler {
    fun handle(action: KompotAction)
}

// A chain of nodes rather than a flat set of fields: the order of application is decided by the
// backend, through the order of calls in its modifier block, instead of being fixed here. fold applies
// the nodes one after another, in the order they arrived.
@Composable
fun List<KompotModifierNode>.toComposeModifier(): Modifier {
    val designSystem = LocalKompotDesignSystem.current

    return fold(Modifier as Modifier) { currentModifier, node ->
        when (node) {
            is KompotModifierNode.Size -> {
                var mod = currentModifier
                // An absolute extent and a symbolic one contradict each other on the same axis, so
                // the number wins and Fill is not applied there. Wrap needs no call at all: it is
                // what Compose already does without a size modifier.
                val widthDp = node.widthDp
                val heightDp = node.heightDp
                // The ceiling goes on FIRST, and the order is the whole of it. Constraints travel
                // outward-in: widthIn placed first narrows what the extent below it may fill, so
                // fillMaxWidth then fills 400. Placed after, it receives constraints whose minimum is
                // already the window — fillMaxWidth fixed both ends at 1200 — and a maximum below the
                // minimum is coerced back up. Written that way round the cap reads correctly, changes
                // nothing, and was measured at 1200.dp against a 400.dp ceiling.
                node.maxWidthDp?.let { mod = mod.widthIn(max = it.dp) }
                node.maxHeightDp?.let { mod = mod.heightIn(max = it.dp) }
                when {
                    widthDp != null -> mod = mod.width(widthDp.dp)
                    node.width == SizeType.Fill -> mod = mod.fillMaxWidth()
                }
                when {
                    heightDp != null -> mod = mod.height(heightDp.dp)
                    node.height == SizeType.Fill -> mod = mod.fillMaxHeight()
                }
                mod
            }

            is KompotModifierNode.Background -> {
                val color = designSystem.resolveColor(node.color)
                val shape = node.role?.let { designSystem.resolveSurface(SurfaceRole(it)).shape }
                    // Clipped as well as painted: a card whose fill is rounded and whose content is
                    // not is a card with square corners under the first child that reaches the edge.
                if (shape == null) currentModifier.background(color) else currentModifier.clip(shape).background(color)
            }

            is KompotModifierNode.Gradient -> {
                if (node.colors.size >= 2) {
                    val colors = node.colors.map { designSystem.resolveColor(it) }
                    currentModifier.background(Brush.verticalGradient(colors))
                } else {
                        // Fewer than two colours is a degenerate gradient; painting one solid colour
                        // is more sensible than silently drawing nothing.
                    node.colors.firstOrNull()?.let { currentModifier.background(designSystem.resolveColor(it)) }
                        ?: currentModifier
                }
            }

            is KompotModifierNode.Padding -> {
                currentModifier.padding(
                    start = (node.start ?: node.all ?: 0).dp,
                    top = (node.top ?: node.all ?: 0).dp,
                    end = (node.end ?: node.all ?: 0).dp,
                    bottom = (node.bottom ?: node.all ?: 0).dp,
                )
            }

                // A scope modifier, applied by the parent inside its own RowScope/ColumnScope. The
                // general mapper ignores it deliberately.
            is KompotModifierNode.Weight -> {
                currentModifier
            }
        }
    }
}
