package io.github.youndie.kompot.studio.diagnostics

import io.github.youndie.kompot.material3.M3Colors
import io.github.youndie.kompot.material3.M3Typography
import io.github.youndie.kompot.spec.KompotProtocol
import io.github.youndie.kompot.spec.tokenUses
import io.github.youndie.kompot.spec.walkJsonObjects
import io.github.youndie.kompot.studio.KompotStudioConfig
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

// THE FIFTH LAYER: what is spelled correctly, valid by the schema, drawn without a word of complaint,
// and still not what anybody meant.
//
// The wire leaves two things open on purpose. WORDS — a button's variant, a surface's tone, a
// counter's state — because a closed enum would take a whole screen down on an older client the day a
// value is added; an unfamiliar word draws the neutral thing instead. And TOKENS — a server names a
// role, the client's design system decides the colour — so an unfamiliar one falls back to the
// built-in palette.
//
// Both degrade silently, and neither is a schema error. A token no kit names is one control in one
// state in Material's purple inside somebody's brand, and the person who finds it is a customer with
// a screenshot.
//
// Warnings rather than errors: every one of these bodies is legal, and half of them are deliberate.
internal fun vocabularyFindings(
    config: KompotStudioConfig,
    body: JsonElement,
): List<Finding> = words(config, body) + tokens(config, body)

// A word a deployment declared the set of. Only those: a field whose vocabulary nobody handed over is
// not checked, because "every string we have not been told about" is every string.
private fun words(
    config: KompotStudioConfig,
    body: JsonElement,
): List<Finding> {
    if (config.vocabulary.isEmpty()) return emptyList()

    return walkJsonObjects(body).flatMap { node ->
        val wireType = (node.value[KompotProtocol.DISCRIMINATOR] as? JsonPrimitive)?.content
        val fields = config.vocabulary[wireType].orEmpty()

        fields.entries.mapNotNull { (field, words) ->
            val value = (node.value[field] as? JsonPrimitive)?.takeIf { it.isString }?.content
            if (value == null || value in words) {
                null
            } else {
                Finding(
                    layer = "vocabulary",
                    path = (node.path + field).toString(),
                    message =
                        "$wireType.$field = \"$value\" is not one of ${words.sorted()} — " +
                            "this client draws the neutral variant",
                    severity = Severity.WARNING,
                )
            }
        }.asSequence()
    }.toList()
}

// A token has to be named in EVERY kit and in BOTH palettes of each, or it is the built-in palette
// that answers for it — in that brand, in that mode, and nowhere else. Reporting per kit and per
// palette rather than once is the point: "missing in brand-b dark" is the sentence somebody can act
// on.
private fun tokens(
    config: KompotStudioConfig,
    body: JsonElement,
): List<Finding> {
    if (config.themes.isEmpty()) return emptyList()

    return tokenUses(body, config.schemas).flatMap { use ->
        // The toolkit's own reference set answers for a token no kit names: those resolve through the
        // Material3 design system, which is a real answer rather than a fallback.
        val builtIn =
            when (use.kind) {
                COLOR_TOKEN -> M3Colors.all.map { it.key }
                TYPOGRAPHY_TOKEN -> M3Typography.all.map { it.key }
                else -> emptyList()
            }
        if (use.value in builtIn) return@flatMap emptyList()

        config.themes.entries.sortedBy { it.key }.flatMap { (brand, theme) ->
            val missing =
                when (use.kind) {
                    COLOR_TOKEN ->
                        listOfNotNull(
                            "light".takeIf { use.value !in theme.light.colors.keys },
                            // A kit with no dark palette says "this brand described no dark theme",
                            // and §6 has the client stay on its built-in palette there. That is the
                            // brand's decision, not a gap, so it is not reported.
                            theme.dark?.let { dark -> "dark".takeIf { use.value !in dark.colors.keys } },
                        )

                    TYPOGRAPHY_TOKEN -> listOfNotNull("typography".takeIf { use.value !in theme.typography.keys })
                    else -> emptyList()
                }

            missing.map { palette ->
                Finding(
                    layer = "vocabulary",
                    path = use.path.toString(),
                    message =
                        "the token \"${use.value}\" is not named in $brand/$palette — " +
                            "it draws the built-in default",
                    severity = Severity.WARNING,
                )
            }
        }
    }
}

private const val COLOR_TOKEN = "ColorToken"
private const val TYPOGRAPHY_TOKEN = "TypographyToken"
