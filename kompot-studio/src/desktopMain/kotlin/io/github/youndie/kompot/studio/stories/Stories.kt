package io.github.youndie.kompot.studio.stories

import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.encodeKompotComponent
import io.github.youndie.kompot.studio.KompotStudioConfig
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

// EVERY COMPONENT, IN EVERY STATE, AS SOMETHING THAT CANNOT GO STALE.
//
// A design canvas usually has a section called exactly that, drawn by hand, and it ages the moment a
// component gains a state. The deployment meanwhile already keeps the two lists that would generate
// it: one filled instance per type, and the words each open field accepts. Nothing here invents a
// story — it multiplies two lists a project maintains for other reasons.
internal data class Story(
    val group: String,
    val name: String,
    // The wire body to open, or null when the deployment has no sample of this type. A missing story
    // is shown rather than skipped: "which of our components has nobody ever drawn" is a question this
    // panel can answer only if the gaps are on it.
    val body: String?,
)

internal fun storiesFor(config: KompotStudioConfig): List<Story> {
    val samples = config.samples.toMap()

    val fromSamples =
        config.samples.map { (wireType, component) ->
            Story(group = wireType, name = "sample", body = config.json.encodeKompotComponent(component))
        }

    val variants =
        config.vocabulary.entries.sortedBy { it.key }.flatMap { (wireType, fields) ->
            val sample = samples[wireType] ?: return@flatMap missing(wireType, fields)

            // Encoded ONCE and then edited as JSON: a variant differs from its sample by one property,
            // and rebuilding the component would mean a `when` over every type a deployment has.
            val encoded = config.json.parseToJsonElement(config.json.encodeKompotComponent(sample)).jsonObject

            fields.entries.sortedBy { it.key }.flatMap { (field, words) ->
                words.sorted().map { word ->
                    Story(
                        group = wireType,
                        name = "$field=$word",
                        body =
                            config.json.encodeToString(
                                JsonObject.serializer(),
                                JsonObject(encoded + (field to JsonPrimitive(word))),
                            ),
                    )
                }
            }
        }

    return fromSamples + variants
}

// A type whose words are known and whose sample is not: the gap named once per word it would have had,
// because that is the shape of the answer — "this state of this component has never been looked at".
private fun missing(
    wireType: String,
    fields: Map<String, Set<String>>,
): List<Story> =
    fields.entries.sortedBy { it.key }.flatMap { (field, words) ->
        words.sorted().map { word -> Story(group = wireType, name = "$field=$word", body = null) }
    }

// The sample list, keyed by wire type. A deployment may list a type twice — two instances of the same
// component are a reasonable thing to keep — and the LAST one wins, which is what associate does and
// what a reader of a list expects the later line to mean.
private fun List<Pair<String, KompotComponent>>.toMap(): Map<String, KompotComponent> = associate { it }
