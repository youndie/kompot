package io.github.youndie.kompot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
                if (node.width == SizeType.Fill) mod = mod.fillMaxWidth()
                if (node.height == SizeType.Fill) mod = mod.fillMaxHeight()
                mod
            }

            is KompotModifierNode.Background -> {
                currentModifier.background(designSystem.resolveColor(node.color))
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
