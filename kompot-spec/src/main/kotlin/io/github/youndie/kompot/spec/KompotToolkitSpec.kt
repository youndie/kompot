package io.github.youndie.kompot.spec

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.modules.plus
import io.github.youndie.kompot.KompotModifierNode
import io.github.youndie.kompot.ColorToken
import io.github.youndie.kompot.TypographyToken
import io.github.youndie.kompot.auth.kompotAuthSerializersModule
import io.github.youndie.kompot.kompotCoreSerializersModule
import io.github.youndie.kompot.forms.KompotFormResponse
import io.github.youndie.kompot.forms.FormPatchRequest
import io.github.youndie.kompot.forms.kompotFormsSerializersModule
import io.github.youndie.kompot.generated.generatedFormsSerializersModule
import io.github.youndie.kompot.generated.generatedImagesSerializersModule
import io.github.youndie.kompot.generated.generatedStandardSerializersModule
import io.github.youndie.kompot.generated.generatedWizardSerializersModule
import io.github.youndie.kompot.navigation.NavigationGraph
import io.github.youndie.kompot.realtime.UpdateComponentMessage
import io.github.youndie.kompot.standard.KompotPageResponse
import io.github.youndie.kompot.commands.kompotCommandsSerializersModule
import io.github.youndie.kompot.form.standard.formStandardSerializersModule
import io.github.youndie.kompot.theme.KompotTheme
import io.github.youndie.kompot.standard.kompotStandardSerializersModule
import io.github.youndie.kompot.wizard.WizardResumeRequest
import io.github.youndie.kompot.wizard.kompotWizardSerializersModule
import io.github.youndie.kompot.form.FormPatch
import io.github.youndie.kompot.form.FormSchema
import io.github.youndie.kompot.wizard.core.WizardTransition

// The spec modules that belong to the toolkit itself: one per Gradle module, exactly as there is one
// schema file per module. Order matters — whoever comes first owns a shared definition — but the list
// is not assembled here: a build may insert its own modules between these, and only the build knows
// where.
object KompotToolkitSpec {
    // The toolkit's own modules in their own order — the spec of a build that uses nothing but this
    // repository. An application does NOT have to reuse this list: it composes its own, inserting its
    // modules where they belong in the ownership chain (see KompotSpec.generateAll).
    val modules: List<KompotSpecModule> =
        listOf(
            core(),
            formCore(),
            formStandard(),
            wizardCore(),
            standard(),
            forms(),
            images(),
            realtime(),
            wizard(),
            navigation(),
            auth(),
            commands(),
            theme(),
        )

    fun core() =
        KompotSpecModule(
            name = "kompot-core",
            description =
                "The open contracts of the protocol: the KompotComponent/KompotAction bases, the closed " +
                    "modifier chain and the design-system tokens",
            serializersModule = kompotCoreSerializersModule,
            // The tokens are listed explicitly even though they are reachable through modifiers and
            // properties anyway: otherwise the owner of a definition would be whichever module referred to
            // a token first (TypographyToken used to end up in kompot-standard.schema.json because of
            // text.style). A core type belongs in the core file regardless of who uses it first.
            roots =
                listOf(
                    KompotModifierNode.serializer().descriptor,
                    ColorToken.serializer().descriptor,
                    TypographyToken.serializer().descriptor,
                ),
            handWritten =
                mapOf(
                    "KompotComponent" to
                        KompotSpec.openHierarchy(
                            description =
                                "A node of the screen tree. The hierarchy is OPEN: a server may send a type the " +
                                    "client does not know, and the client must degrade to a placeholder rather than " +
                                    "fail (see SPEC.md, \"Unknown types\").",
                            degrades = true,
                            extraRequired = listOf("id"),
                            extraProperties =
                                buildJsonObject {
                                    putJsonObject("id") {
                                        put("type", "string")
                                        put(
                                            "description",
                                            "Unique within one screen tree: point updates are addressed by it " +
                                                "(see UpdateComponentMessage in :kompot-realtime)",
                                        )
                                    }
                                    putJsonObject("modifiers") {
                                        put("type", "array")
                                        put("items", buildJsonObject { put("\$ref", "#/\$defs/KompotModifierNode") })
                                        put("description", "The order of nodes matters — they are applied left to right")
                                    }
                                },
                        ),
                    "KompotAction" to
                        KompotSpec.openHierarchy(
                            description =
                                "An intent the server sends the client in response to an interaction. The " +
                                    "hierarchy is OPEN on the same terms as KompotComponent.",
                            degrades = true,
                        ),
                ),
        )

    // form-core describes a form's data — validation, values, visibility conditions — and is deliberately
    // separate from kompot-forms, which describes its UI. They are separate schema files for the same
    // reason they are separate Gradle modules: a server may serve a FormSchema without a component tree.
    fun formCore() =
        KompotSpecModule(
            name = "form-core",
            description = "The form contracts: schema, field definitions, validation rules, values, visibility conditions",
            roots = listOf(FormSchema.serializer().descriptor, FormPatch.serializer().descriptor),
            handWritten =
                mapOf(
                    // AN IMPORTANT DIFFERENCE FROM KompotComponent/KompotAction: these four hierarchies have NO
                    // defaultDeserializer — it is registered only in kompotCoreSerializersModule. An unknown type
                    // here fails the parse of the WHOLE response instead of degrading to a placeholder. Hence
                    // x-kompot-degrades: false, a machine-readable warning to an implementation.
                    "FormFieldDefinition" to
                        KompotSpec.openHierarchy(
                            description =
                                "The definition of a form field: the data contract, not its presentation. The " +
                                    "hierarchy is extended by plug-ins but has NO runtime fallback: an unknown type " +
                                    "breaks the parse of the whole form schema.",
                            degrades = false,
                            extraRequired = listOf("fieldId"),
                            extraProperties =
                                buildJsonObject {
                                    putJsonObject("fieldId") {
                                        put("type", "string")
                                        put(
                                            "description",
                                            "Unique within a FormSchema; ties the definition to the UI component " +
                                                "that refers to it",
                                        )
                                    }
                                    putJsonObject("rules") {
                                        put("type", "array")
                                        put("items", buildJsonObject { put("\$ref", "#/\$defs/ValidationRule") })
                                    }
                                    putJsonObject("visibleIf") {
                                        put(
                                            "anyOf",
                                            JsonArray(
                                                listOf(
                                                    buildJsonObject { put("\$ref", "#/\$defs/FormCondition") },
                                                    buildJsonObject { put("type", "null") },
                                                ),
                                            ),
                                        )
                                        put("description", "Evaluated by the client locally, with no round trip to the server")
                                    }
                                    putJsonObject("triggersPatch") {
                                        put("type", "boolean")
                                        put("description", "Changing the value requires asking the server for a patch")
                                    }
                                },
                        ),
                    "ValidationRule" to
                        KompotSpec.openHierarchy(
                            description =
                                "A client-side validation rule for a field. No runtime fallback (see FormFieldDefinition).",
                            degrades = false,
                            extraRequired = listOf("errorMessage"),
                            extraProperties =
                                buildJsonObject {
                                    putJsonObject("errorMessage") {
                                        put("type", "string")
                                        put("description", "Ready localised error text, not a translation key")
                                    }
                                },
                        ),
                    "FieldValue" to
                        KompotSpec.openHierarchy(
                            description =
                                "The value of a form field. It travels both ways: server -> client in a patch, " +
                                    "client -> server on submit. No runtime fallback.",
                            degrades = false,
                        ),
                    "FormCondition" to
                        KompotSpec.openHierarchy(
                            description = "A field's visibility condition (visibleIf). No runtime fallback.",
                            degrades = false,
                        ),
                ),
        )

    fun standard() =
        KompotSpecModule(
            name = "kompot-standard",
            description = "The basic layout and text set: containers, text, button, table, paginated list",
            serializersModule = kompotStandardSerializersModule + generatedStandardSerializersModule,
            roots = listOf(KompotPageResponse.serializer().descriptor),
            annotations =
                mapOf(
                    "KompotActionNavigate" to
                        mapOf(
                            "deeplink" to
                                KompotSpec.constrained(
                                    KompotProtocol.DEEPLINK_PATTERN,
                                    "The identifier of an application screen: a URI with its own scheme, not a web " +
                                        "address. Both the scheme and the set of values are the application's (see " +
                                        "NavigationGraph in :kompot-navigation); a client must ignore an unknown deeplink",
                                    forbid = KompotProtocol.DEEPLINK_FORBIDDEN_PATTERN,
                                ),
                        ),
                    "LoadPage" to mapOf("url" to KompotSpec.constrained(KompotProtocol.ENDPOINT_PATTERN, "The relative address of the next page")),
                    "KompotActionLoadPage" to
                        mapOf("url" to KompotSpec.constrained(KompotProtocol.ENDPOINT_PATTERN, "The relative address of the next page")),
                    "KompotComponentPaginatedList" to
                        mapOf(
                            "reloadUrl" to
                                KompotSpec.constrained(
                                    KompotProtocol.ENDPOINT_PATTERN,
                                    "The relative address of the first page; the form's field values go there as query parameters",
                                ),
                        ),
                ),
        )

    fun forms() =
        KompotSpecModule(
            name = "kompot-forms",
            description = "The form UI: input components, the submit action and the form/patch response envelopes",
            serializersModule = kompotFormsSerializersModule + generatedFormsSerializersModule,
            roots = listOf(KompotFormResponse.serializer().descriptor, FormPatchRequest.serializer().descriptor),
            annotations =
                mapOf(
                    "KompotFormResponse" to
                        mapOf(
                            "realtimeTopic" to
                                KompotSpec.constrained(
                                    KompotProtocol.REALTIME_TOPIC_PATTERN,
                                    "The live-update topic of this screen. The string is opaque to the client; a server " +
                                        "must make it per-subject wherever the data is personal (see SPEC.md §10.4)",
                                ),
                        ),
                    "SelectOption" to mapOf("rawMetadata" to KompotSpec.reservedMetadata()),
                ),
        )

    // The only wire type of wizard-core is the transition itself. Everything else — the session, the
    // engine, the step resolvers — lives on the server and never travels.
    // The concrete fields, rules, values and conditions. It belongs to the toolkit's own list and not
    // to each consumer's, even though it is a plug-in: the annotations below are protocol knowledge —
    // §9.7 reserves the metadata keys and calls the default part of the protocol — and knowledge every
    // consumer copies word for word was living in the wrong place. Two independent implementations had
    // already reproduced this declaration verbatim (issue #2).
    //
    // Right after formCore(), whose four open bases it fills in.
    fun formStandard() =
        KompotSpecModule(
            name = "form-standard",
            description = "The standard form fields, rules, values and conditions over form-core",
            serializersModule = formStandardSerializersModule,
            annotations =
                mapOf(
                    "FieldValueEntityValue" to mapOf("rawMetadata" to KompotSpec.reservedMetadata()),
                    "ValidationRuleMaxAmountFromField" to
                        mapOf(
                            "balanceMetadataKey" to
                                KompotSpec.constrained(
                                    pattern = null,
                                    description =
                                        "The key in the chosen entity_value's rawMetadata the remaining amount is read " +
                                            "from. Defaults to \"${KompotProtocol.METADATA_KEY_BALANCE}\"",
                                ),
                        ),
                ),
        )

    fun wizardCore() =
        KompotSpecModule(
            name = "wizard-core",
            description = "A transition of a multi-step flow (Next/Back/Finish/JumpTo)",
            roots = listOf(WizardTransition.serializer().descriptor),
        )

    fun images() =
        KompotSpecModule(
            name = "kompot-images",
            description = "A content image by URL",
            serializersModule = generatedImagesSerializersModule,
        )

    // The transport is not part of the spec: this is the contract of a FRAME, not of a channel — the same
    // way :kompot-realtime itself knows nothing about any HTTP library.
    fun realtime() =
        KompotSpecModule(
            name = "kompot-realtime",
            description = "One frame of the component live-update channel",
            roots = listOf(UpdateComponentMessage.serializer().descriptor),
        )

    fun wizard() =
        KompotSpecModule(
            name = "kompot-wizard",
            description = "The step screen of a flow, the Next/Back/Finish transitions and the result of a step",
            serializersModule = kompotWizardSerializersModule + generatedWizardSerializersModule,
            roots = listOf(WizardResumeRequest.serializer().descriptor),
        )

    fun navigation() =
        KompotSpecModule(
            name = "kompot-navigation",
            description = "The route graph of plain screens: deeplink -> endpoint",
            roots = listOf(NavigationGraph.serializer().descriptor),
            annotations =
                mapOf(
                    "ScreenRoute" to
                        mapOf(
                            "deeplink" to
                                KompotSpec.constrained(
                                    KompotProtocol.DEEPLINK_PATTERN,
                                    "Matches the string a server puts into navigate.deeplink",
                                    forbid = KompotProtocol.DEEPLINK_FORBIDDEN_PATTERN,
                                ),
                            "endpoint" to KompotSpec.constrained(KompotProtocol.ENDPOINT_PATTERN, "The relative address the screen tree is fetched from"),
                        ),
                ),
        )

    fun auth() =
        KompotSpecModule(
            name = "kompot-auth",
            description = "The action that updates the session after a login",
            serializersModule = kompotAuthSerializersModule,
        )

    // After formCore() in the list, and it has to be: the payload's values are FieldValue, and the
    // first module to mention a definition owns it. Ahead of form-core this would move the whole value
    // hierarchy into a file about buttons.
    fun commands() =
        KompotSpecModule(
            name = "kompot-commands",
            description = "The action that performs an operation on one item of a list, with no form around it",
            serializersModule = kompotCommandsSerializersModule,
            annotations =
                mapOf(
                    "KompotActionPerform" to
                        mapOf(
                            "url" to
                                KompotSpec.constrained(
                                    KompotProtocol.ENDPOINT_PATTERN,
                                    "The relative address of an endpoint of kind `submit`: it answers a KompotAction, " +
                                        "which the client runs through the same handler chain as any other intent. " +
                                        "Being state-changing, it requires an Idempotency-Key (§16.5)",
                                ),
                            "payload" to
                                KompotSpec.constrained(
                                    pattern = null,
                                    description =
                                        "What the operation acts on and with: the identity of the item, plus any " +
                                            "parameters. The keys are the application's, the values are the same " +
                                            "FieldValue vocabulary a form submit sends. Two buttons on two items of " +
                                            "one list differ in this and nothing else",
                                ),
                        ),
                ),
        )

    // A theme is served, so it is protocol — and it was outside the declared profile, which is what
    // made the omission worth a report rather than a shrug: an implementation reading the published
    // contract had no description of a document its server is expected to answer with.
    //
    // No serializersModule: nothing here joins a polymorphic hierarchy. KompotTheme is a root of its
    // own, the way NavigationGraph and UpdateComponentMessage are.
    fun theme() =
        KompotSpecModule(
            name = "kompot-theme",
            description = "A server-driven theme: what a client resolves its design-system tokens into",
            roots = listOf(KompotTheme.serializer().descriptor),
            annotations =
                mapOf(
                    "KompotPalette" to
                        mapOf(
                            "colors" to
                                KompotSpec.constrained(
                                    pattern = null,
                                    description =
                                        "ColorToken key -> colour. The value is a hex string (#RGB, #RRGGBB or " +
                                            "#AARRGGBB, with or without the hash); alpha defaults to opaque. A malformed " +
                                            "value is treated as an absent one — the client keeps its built-in colour",
                                ),
                        ),
                    "KompotTheme" to
                        mapOf(
                            "dark" to
                                KompotSpec.constrained(
                                    pattern = null,
                                    description =
                                        "Absent means the brand described no dark theme. A client MUST then stay entirely " +
                                            "on its built-in dark palette rather than substituting the light one",
                                ),
                            "typography" to
                                KompotSpec.constrained(
                                    pattern = null,
                                    description =
                                        "TypographyToken key -> style. One set for both themes. Every property of a style " +
                                            "is optional: what is absent keeps the client's built-in value",
                                ),
                        ),
                ),
        )
}
