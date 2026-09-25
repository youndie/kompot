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
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotRegistry
import io.github.youndie.kompot.form.standard.formStandardSerializersModule
import io.github.youndie.kompot.generated.generatedFormsClientRenderers
import io.github.youndie.kompot.kompotCoreRenderers
import io.github.youndie.kompot.kompotJson
import io.github.youndie.kompot.kompotStandardRenderers
import io.github.youndie.kompot.playground.demo.demoRenderers
import io.github.youndie.kompot.playground.demo.demoSerializersModule
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.SerializersModuleCollector
import kotlinx.serialization.modules.plus
import kotlin.reflect.KClass

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
    // Today's client: every word of the body is in its vocabulary, and it can draw every one.
    CURRENT(
        "Today's client",
        "Its serializers module knows every type in the body and its registry has a renderer for each.",
    ),

    // Released before the chosen word existed. It cannot decode the type, so the node becomes
    // UnknownComponent — and with nothing to put there, nothing is drawn. The screen survives; the
    // node does not. This is the state the whole protocol is arranged around.
    OLDER(
        "A client released earlier",
        "Its serializers module has never heard of the chosen type. The node decodes to UnknownComponent, " +
            "nothing is drawn in its place, and the rest of the screen is untouched.",
    ),

    // The same old client, and a server that named a stand-in.
    OLDER_WITH_FALLBACK(
        "The same client, server named an equivalent",
        "The body now carries a fallback — an ordinary component the server chose. The old client cannot " +
            "read the chosen type and draws that instead.",
    ),

    // A DIFFERENT hole, and the reason the page shows four states rather than three: here the type
    // decodes perfectly and the registry has no renderer for it. That is a build assembled without a
    // plug-in, not a client older than the server, and the toolkit tells them apart — this one gets a
    // visible placeholder, the one above gets silence.
    MISSING_RENDERER(
        "A build without the renderer",
        "It understands the chosen type and cannot draw it: a different hole, with a visible placeholder and a " +
            "different name in the log.",
    ),
    ;

    // Whether the SERVER put an equivalent in the body for this state.
    public val serverNamesFallback: Boolean get() = this == OLDER_WITH_FALLBACK
}

/**
 * One client the page can put in front of the body: a [mode] and the one word of the vocabulary it is
 * missing, [unfamiliar] — a wire name, `box` or `promo_banner` alike (B-59).
 *
 * The older client is TODAY's client with that one registration taken out, not a second vocabulary
 * written by hand: the page asks "what happens if the client does not know this word", and a client
 * that differed in anything else would answer a different question. Taking it out of the module
 * rather than renaming the node in the body is also what keeps the log honest — the type it reports
 * is the one the body sent.
 */
public class PlaygroundClient(
    public val mode: ClientMode,
    public val unfamiliar: String,
) {
    public val json: Json by lazy {
        when (mode) {
            ClientMode.CURRENT, ClientMode.MISSING_RENDERER -> TODAY_JSON
            ClientMode.OLDER, ClientMode.OLDER_WITH_FALLBACK ->
                Json(from = TODAY_JSON) { serializersModule = TODAY_JSON.serializersModule.withoutComponent(unfamiliar) }
        }
    }

    // An older client has no renderer for a word it never heard of either; a build without the renderer
    // is missing exactly that. Only today's client has them all.
    public val registry: KompotRegistry by lazy {
        when (mode) {
            ClientMode.CURRENT -> KompotRegistry(TODAY_RENDERERS)
            else -> KompotRegistry(TODAY_RENDERERS - listOfNotNull(TODAY_COMPONENTS[unfamiliar]).toSet())
        }
    }
}

// The form vocabulary is part of today's client, and so of every older one: the switch models a client
// that is older by ONE word, not one that speaks half the protocol.
private val TODAY_JSON: Json = kompotJson(formStandardSerializersModule + demoSerializersModule)

private val TODAY_RENDERERS = kompotCoreRenderers + kompotStandardRenderers + generatedFormsClientRenderers + demoRenderers

/** Every component type today's client decodes, by wire name — read off its module, not listed. */
@OptIn(ExperimentalSerializationApi::class)
internal val TODAY_COMPONENTS: Map<String, KClass<out KompotComponent>> by lazy {
    val found = linkedMapOf<String, KClass<out KompotComponent>>()
    TODAY_JSON.serializersModule.dumpTo(
        object : Collecting() {
            override fun <Base : Any, Sub : Base> polymorphic(
                baseClass: KClass<Base>,
                actualClass: KClass<Sub>,
                actualSerializer: KSerializer<Sub>,
            ) {
                @Suppress("UNCHECKED_CAST")
                if (baseClass == KompotComponent::class) found[actualSerializer.descriptor.serialName] = actualClass as KClass<out KompotComponent>
            }
        },
    )
    found
}

// The same module with one component registration left out, and everything else — the unknown
// fallback deserializer the old client degrades through included — copied as it was.
@OptIn(ExperimentalSerializationApi::class)
private fun SerializersModule.withoutComponent(wireName: String): SerializersModule =
    SerializersModule {
        val into = this
        dumpTo(
            object : SerializersModuleCollector {
                override fun <T : Any> contextual(
                    kClass: KClass<T>,
                    provider: (typeArgumentsSerializers: List<KSerializer<*>>) -> KSerializer<*>,
                ) = into.contextual(kClass, provider)

                override fun <Base : Any, Sub : Base> polymorphic(
                    baseClass: KClass<Base>,
                    actualClass: KClass<Sub>,
                    actualSerializer: KSerializer<Sub>,
                ) {
                    if (baseClass == KompotComponent::class && actualSerializer.descriptor.serialName == wireName) return
                    into.polymorphic(baseClass, actualClass, actualSerializer)
                }

                override fun <Base : Any> polymorphicDefaultSerializer(
                    baseClass: KClass<Base>,
                    defaultSerializerProvider: (value: Base) -> SerializationStrategy<Base>?,
                ) = into.polymorphicDefaultSerializer(baseClass, defaultSerializerProvider)

                override fun <Base : Any> polymorphicDefaultDeserializer(
                    baseClass: KClass<Base>,
                    defaultDeserializerProvider: (className: String?) -> DeserializationStrategy<Base>?,
                ) = into.polymorphicDefaultDeserializer(baseClass, defaultDeserializerProvider)
            },
        )
    }

// A collector that only listens for what it overrides.
@OptIn(ExperimentalSerializationApi::class)
private abstract class Collecting : SerializersModuleCollector {
    override fun <T : Any> contextual(
        kClass: KClass<T>,
        provider: (typeArgumentsSerializers: List<KSerializer<*>>) -> KSerializer<*>,
    ) = Unit

    override fun <Base : Any> polymorphicDefaultSerializer(
        baseClass: KClass<Base>,
        defaultSerializerProvider: (value: Base) -> SerializationStrategy<Base>?,
    ) = Unit

    override fun <Base : Any> polymorphicDefaultDeserializer(
        baseClass: KClass<Base>,
        defaultDeserializerProvider: (className: String?) -> DeserializationStrategy<Base>?,
    ) = Unit
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ClientSwitch(
    client: PlaygroundClient,
    words: List<String>,
    onModeChange: (ClientMode) -> Unit,
    onWordChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ClientMode.entries.forEach { candidate ->
                FilterChip(
                    selected = candidate == client.mode,
                    onClick = { onModeChange(candidate) },
                    label = { Text(candidate.title) },
                )
            }
        }

        // The word the other clients lack, chosen among the ones the body actually sends: a type that
        // is not in the body would make every state look the same and prove nothing.
        Text("The type it does not know", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            words.forEach { word ->
                FilterChip(
                    selected = word == client.unfamiliar,
                    onClick = { onWordChange(word) },
                    label = { Text(word) },
                )
            }
        }

        // The sentence matters as much as the chip: "nothing is drawn" looks like a bug until somebody
        // says it is the protocol working.
        Text(
            client.mode.explanation,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
