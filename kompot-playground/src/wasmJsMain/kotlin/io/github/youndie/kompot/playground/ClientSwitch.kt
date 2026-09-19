package io.github.youndie.kompot.playground

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.youndie.kompot.KompotRegistry
import io.github.youndie.kompot.form.standard.formStandardSerializersModule
import io.github.youndie.kompot.generated.generatedFormsClientRenderers
import io.github.youndie.kompot.kompotCoreRenderers
import io.github.youndie.kompot.kompotJson
import io.github.youndie.kompot.kompotStandardRenderers
import io.github.youndie.kompot.playground.demo.demoRenderers
import io.github.youndie.kompot.playground.demo.demoSerializersModule
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.plus

// WHICH CLIENT IS LOOKING AT THE BODY — the whole point of the page.
//
// A client is two things, and they are not the same thing: a serializers module, which decides what
// it UNDERSTANDS, and a renderer registry, which decides what it DRAWS. Every state below is one
// combination of those two, and the body on the left does not change with them — that is the claim
// the page exists to make, and swapping bodies instead would be a magic trick.
//
// The one exception is FALLBACK, and it is not the client's at all: only a server can name an
// equivalent for a component it chose to send (SPEC.md §2.1). So that state adds a key to the BODY,
// visibly, in the editor — the page says out loud that this half came from the other end of the wire.
public enum class ClientMode(
    public val title: String,
    public val explanation: String,
) {
    // Today's client: it has the plug-in, both halves of it.
    CURRENT(
        "Today's client",
        "Ships the plug-in: its serializers module knows promo_banner and its registry has a renderer for it.",
    ),

    // Released before the component existed. It cannot decode the type, so the node becomes
    // UnknownComponent — and with nothing to put there, nothing is drawn. The screen survives; the
    // node does not. This is the state the whole protocol is arranged around.
    OLDER(
        "A client released earlier",
        "Its serializers module has never heard of promo_banner. The node decodes to UnknownComponent, " +
            "nothing is drawn in its place, and the rest of the screen is untouched.",
    ),

    // The same old client, and a server that named a stand-in.
    OLDER_WITH_FALLBACK(
        "The same client, server named an equivalent",
        "The body now carries a fallback — an ordinary component the server chose. The old client cannot " +
            "read promo_banner and draws that instead.",
    ),

    // A DIFFERENT hole, and the reason the page shows four states rather than three: here the type
    // decodes perfectly and the registry has no renderer for it. That is a build assembled without a
    // plug-in, not a client older than the server, and the toolkit tells them apart — this one gets a
    // visible placeholder, the one above gets silence.
    MISSING_RENDERER(
        "A build without the plug-in's renderer",
        "It understands the type and cannot draw it: a different hole, with a visible placeholder and a " +
            "different name in the log.",
    ),
    ;

    // What the mode means in the two halves a client is made of.
    // The form vocabulary is in EVERY mode, and deliberately: the switch models a client that is
    // older by one component, not one that speaks half the protocol. Leaving it out of the older modes
    // would make the form example degrade for a second reason and blur the one the page is about.
    public val json: Json
        get() =
            when (this) {
                CURRENT, MISSING_RENDERER -> kompotJson(formStandardSerializersModule + demoSerializersModule)
                OLDER, OLDER_WITH_FALLBACK -> kompotJson(formStandardSerializersModule)
            }

    public val registry: KompotRegistry
        get() =
            when (this) {
                CURRENT ->
                    KompotRegistry(kompotCoreRenderers + kompotStandardRenderers + generatedFormsClientRenderers + demoRenderers)
                // No renderer for the demo type, and no UnknownComponentRenderer either would be a third
                // thing again — the core map is what any client has.
                OLDER, OLDER_WITH_FALLBACK, MISSING_RENDERER ->
                    KompotRegistry(kompotCoreRenderers + kompotStandardRenderers + generatedFormsClientRenderers)
            }

    // Whether the SERVER put an equivalent in the body for this state.
    public val serverNamesFallback: Boolean get() = this == OLDER_WITH_FALLBACK
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ClientSwitch(
    mode: ClientMode,
    onChange: (ClientMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ClientMode.entries.forEach { candidate ->
                FilterChip(
                    selected = candidate == mode,
                    onClick = { onChange(candidate) },
                    label = { Text(candidate.title) },
                )
            }
        }

        // The sentence matters as much as the chip: "nothing is drawn" looks like a bug until somebody
        // says it is the protocol working.
        Text(
            mode.explanation,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
