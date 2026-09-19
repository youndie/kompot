package io.github.youndie.kompot.studio

import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.encodeKompotComponent
import io.github.youndie.kompot.spec.KompotProtocol
import io.github.youndie.kompot.studio.palette.profileComponentTypes
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

// THE WIRE NAME OF A SAMPLE, DERIVED RATHER THAN ASKED FOR.
//
// `samples` used to be pairs: the deployment wrote the wire name of every component by hand, because
// the toolkit offers no way to compute one — `KompotComponent.wireType()` is internal to the client
// and has no public equivalent. A wrong string was SILENT: the palette matches the name against the
// profile, a miss left `hasSample = false`, and the panel looked exactly like one belonging to a
// deployment that had declared no samples at all. The readme of this module even documented a
// `wireTypeOf` that was never written, and nobody found out by using it (B-37, B-38).
//
// Derived through the ENCODER rather than through a serializer lookup, and that is the point: the
// name this returns is the string the deployment's own Json writes into the body, discriminator and
// all. A helper reading `serializerOrNull()?.descriptor?.serialName` would answer a question about
// the class; this answers the question about the wire, and they can differ — a type absent from the
// Json's serializers module has no wire name at all, and here that is an error rather than a
// plausible-looking string.
internal fun samplesByWireType(config: KompotStudioConfig): Map<String, KompotComponent> {
    val byType = LinkedHashMap<String, KompotComponent>()
    config.samples.forEach { sample ->
        val wireType = wireTypeOf(config, sample)
        val clash = byType.put(wireType, sample)
        require(clash == null) {
            "two samples encode as \"$wireType\" — ${clash!!::class.simpleName} and " +
                "${sample::class.simpleName}. One filled instance per type is what the panel shows; " +
                "with two it would show whichever came last and say nothing about the other."
        }
    }
    return byType
}

private fun wireTypeOf(
    config: KompotStudioConfig,
    sample: KompotComponent,
): String {
    val name = sample::class.simpleName ?: "a sample"
    val encoded =
        runCatching { config.json.parseToJsonElement(config.json.encodeKompotComponent(sample)).jsonObject }
            .getOrElse { failure ->
                throw IllegalArgumentException(
                    "the sample $name cannot be encoded by this configuration's Json, so it has no " +
                        "wire name — and neither would it have one on a real response. Register its " +
                        "type in the serializers module the studio is configured with.",
                    failure,
                )
            }

    // A polymorphic encoding always writes the discriminator; a body without one is a component
    // encoded through its concrete serialiser, which is the very bug `respondKompotComponent` exists
    // to prevent. Named here rather than defaulted, because guessing would hide it.
    return (encoded[KompotProtocol.DISCRIMINATOR] as? JsonPrimitive)?.content
        ?: error(
            "the sample $name encoded without a \"${KompotProtocol.DISCRIMINATOR}\" — it went through " +
                "its concrete serialiser instead of the polymorphic one, and a client would meet it " +
                "as an unknown component.",
        )
}

// A SAMPLE OF A TYPE THE PROFILE DOES NOT KNOW IS A TYPO, AND IT USED TO BE INVISIBLE.
//
// Deriving the name removes the misspelt-string half of that; this is the other half. A component
// whose type is in no schema cannot be built by the palette, checked by the body rules or drawn from
// a recording, so a sample of it is a sample of nothing — and the panel's silence looked identical to
// having no sample.
//
// No profile means no closed list to check against, and the studio already treats that as "this
// deployment handed over no vocabulary" rather than as an error (the palette is empty then too).
// `extensionTypes` is the deployment's own declaration that a type lives outside the profile on
// purpose, and it counts here for the same reason it counts in the body check.
internal fun checkSamples(config: KompotStudioConfig) {
    val known = profileComponentTypes(config)
    if (known.isEmpty()) return

    val unknown = samplesByWireType(config).keys.filterNot { it in known || it in config.extensionTypes }
    require(unknown.isEmpty()) {
        "these samples have types no schema declares: ${unknown.sorted().joinToString()}. " +
            "The palette builds from the profile, so a sample outside it can never be reached — " +
            "add the type's module to `schemas`, or name it in `extensionTypes` if it is deliberately " +
            "outside the profile."
    }
}
