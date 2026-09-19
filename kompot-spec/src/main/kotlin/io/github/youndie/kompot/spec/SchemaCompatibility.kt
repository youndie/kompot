package io.github.youndie.kompot.spec

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// WHAT A CHANGE OF THE SCHEMA COSTS THE OTHER END, decided by the rules SPEC.md §15 already states.
//
// §15 lists what is compatible and what is not, and then said the diff of schema/*.json in a pull
// request was the whole mechanism. A diff of a generated file is a bad place to spot the difference
// that matters: adding a component and adding a form field look the same there — a block of new JSON
// under $defs and a line in a mapping — while the first is compatible by protocol and the second
// breaks every client released before it (§2.2: the form hierarchies have no fallback, so an
// unfamiliar type costs the whole response rather than one node).
//
// So the classification happens here, over the two sets of documents, and every finding names the
// §15 bullet it applied. What §15 does NOT name comes back as UNCLASSIFIED rather than as silence: a
// checker that passes everything it has no rule for covers the shape it was written against and
// nothing else.
//
// Pure on purpose — two sets of documents in, findings out. Where the old set comes from (git, a
// published artefact, a directory) is the caller's business, and an application checking ITS schema
// against ITS own history reuses exactly this.
public enum class Compatibility {
    // §15 names the change as compatible: it needs no change of the protocol version.
    COMPATIBLE,

    // §15 names the change as incompatible: forbidden without a change of the protocol version, and
    // therefore without an entry in the journal of §13.
    BREAKING,

    // A real change of the wire shape that §15 names on neither list. Not silence and not a verdict:
    // it is a question for the reviewer, and until §15 grows a rule for it the safe reading is that
    // it is incompatible.
    UNCLASSIFIED,
}

public data class SchemaChange(
    // The rule's name, in the vocabulary of SchemaCompatibilityRules: "type-added-degrading",
    // "required-added". An id rather than a sentence, so that a report can be grepped and a test can
    // name what it expects.
    val rule: String,
    val verdict: Compatibility,
    // What changed, named the way a reader of the schema names it: "KompotComponent/text" for a
    // member of a hierarchy, "KompotComponentText.maxLines" for a property of a definition.
    val subject: String,
    val message: String,
) {
    override fun toString(): String = "$verdict $rule $subject — $message"
}

public object SchemaCompatibilityRules {
    // --- compatible (§15, first list) ---
    public const val TYPE_ADDED_DEGRADING: String = "type-added-degrading"
    public const val FIELD_ADDED_OPTIONAL: String = "field-added-optional"
    public const val DEFINITION_ADDED: String = "definition-added"

    // --- incompatible (§15, second list) ---
    public const val TYPE_REMOVED: String = "type-removed"
    public const val TYPE_ADDED_NO_DEGRADATION: String = "type-added-no-degradation"
    public const val CLOSED_HIERARCHY_EXTENDED: String = "closed-hierarchy-extended"
    public const val REQUIRED_ADDED: String = "required-added"
    public const val REQUIRED_REMOVED: String = "required-removed"
    public const val FIELD_TYPE_CHANGED: String = "field-type-changed"
    public const val DISCRIMINATOR_RENAMED: String = "discriminator-renamed"

    // --- on neither list of §15 ---
    public const val FIELD_REMOVED: String = "field-removed"
    public const val HIERARCHY_CONTRACT_CHANGED: String = "hierarchy-contract-changed"
    public const val DEFINITION_KIND_CHANGED: String = "definition-kind-changed"
    public const val UNKNOWN_FIELDS_CLOSED: String = "unknown-fields-closed"
    public const val UNCLASSIFIED_CHANGE: String = "unclassified-change"
}

public object SchemaCompatibility {
    // The old set and the new one, each keyed by file name exactly as SchemaFiles.loadAll() returns
    // them — the profile included, because the closed list of types a build emits lives only there.
    public fun compare(
        old: Map<String, JsonObject>,
        new: Map<String, JsonObject>,
    ): List<SchemaChange> {
        val before = model(old)
        val after = model(new)

        // Definitions whose appearance or disappearance a hierarchy has already reported by wire
        // name. Without this a renamed type reads as four findings instead of two.
        val spoken = mutableSetOf<String>()

        val changes =
            hierarchyChanges(before, after, spoken) +
                definitionChanges(before, after, spoken) +
                residualChanges(before, after)

        return changes.sortedWith(compareBy({ it.verdict.ordinal }, { it.subject }, { it.rule }))
    }

    // ---- the hierarchies: which types exist, and what an unfamiliar one costs ----

    private fun hierarchyChanges(
        before: Model,
        after: Model,
        spoken: MutableSet<String>,
    ): List<SchemaChange> {
        val changes = mutableListOf<SchemaChange>()

        (before.hierarchies.keys + after.hierarchies.keys).sorted().forEach { name ->
            val old = before.hierarchies[name]
            val now = after.hierarchies[name]

            if (now == null) {
                changes +=
                    SchemaChange(
                        SchemaCompatibilityRules.TYPE_REMOVED,
                        Compatibility.BREAKING,
                        name,
                        "the hierarchy is gone, and every type in it with it — §15: removal or rename of a type",
                    )
                return@forEach
            }
            if (old == null) {
                changes +=
                    SchemaChange(
                        SchemaCompatibilityRules.DEFINITION_ADDED,
                        Compatibility.COMPATIBLE,
                        name,
                        "a hierarchy nothing could reference before; what it costs is decided where a field starts " +
                            "naming it, and that shows up as a change of that field",
                    )
                return@forEach
            }

            if (old.discriminator != now.discriminator) {
                changes +=
                    SchemaChange(
                        SchemaCompatibilityRules.DISCRIMINATOR_RENAMED,
                        Compatibility.BREAKING,
                        name,
                        "the discriminator is \"${now.discriminator}\" where it was \"${old.discriminator}\" — " +
                            "§15: a change of the discriminator property's name",
                    )
            }

            if (old.open != now.open || old.degrades != now.degrades) {
                changes +=
                    SchemaChange(
                        SchemaCompatibilityRules.HIERARCHY_CONTRACT_CHANGED,
                        Compatibility.UNCLASSIFIED,
                        name,
                        "open=${old.open}/degrades=${old.degrades} became open=${now.open}/degrades=${now.degrades}. " +
                            "§15 names no rule for it, and it changes what EVERY unfamiliar type of this hierarchy " +
                            "costs a reader (§2.1–2.3)",
                    )
            }

            (old.members - now.members).sorted().forEach { wireType ->
                before.definitionOf(wireType)?.let { spoken += it }
                changes +=
                    SchemaChange(
                        SchemaCompatibilityRules.TYPE_REMOVED,
                        Compatibility.BREAKING,
                        "$name/$wireType",
                        "the type is gone from the hierarchy — §15: removal or rename of a type. A rename shows up " +
                            "here as a removal beside an addition",
                    )
            }

            (now.members - old.members).sorted().forEach { wireType ->
                after.definitionOf(wireType)?.let { spoken += it }
                changes += memberAdded(name, wireType, now)
            }
        }

        return changes
    }

    private fun memberAdded(
        hierarchy: String,
        wireType: String,
        facts: Hierarchy,
    ): SchemaChange =
        when {
            // §2.3: the set of nodes is fixed by the protocol and no degradation is provided for one
            // of them — parsing the component that carries it fails outright.
            !facts.open ->
                SchemaChange(
                    SchemaCompatibilityRules.CLOSED_HIERARCHY_EXTENDED,
                    Compatibility.BREAKING,
                    "$hierarchy/$wireType",
                    "a new member of a CLOSED hierarchy — §15: adding a node to the closed hierarchy. A reader " +
                        "released before it cannot parse the node that carries it",
                )

            facts.degrades == true ->
                SchemaChange(
                    SchemaCompatibilityRules.TYPE_ADDED_DEGRADING,
                    Compatibility.COMPATIBLE,
                    "$hierarchy/$wireType",
                    "a new type in a hierarchy WITH degradation — §15: compatible. A reader that does not know it " +
                        "draws a placeholder (§2.1)",
                )

            // §2.2, and the most expensive line of the protocol: the form hierarchies are open and
            // have no fallback, so an unfamiliar type costs the whole response.
            else ->
                SchemaChange(
                    SchemaCompatibilityRules.TYPE_ADDED_NO_DEGRADATION,
                    Compatibility.BREAKING,
                    "$hierarchy/$wireType",
                    "a new type in a hierarchy WITHOUT degradation — §15: releasable only once readers that support " +
                        "it are rolled out. For an older one this is not a new variant but a parse error of the " +
                        "whole response (§2.2)",
                )
        }

    // ---- the definitions: their fields, the types of those fields, and what is required ----

    private fun definitionChanges(
        before: Model,
        after: Model,
        spoken: Set<String>,
    ): List<SchemaChange> {
        val changes = mutableListOf<SchemaChange>()

        (before.definitions.keys + after.definitions.keys).sorted().forEach { name ->
            val old = before.definitions[name]
            val now = after.definitions[name]

            // A variant leaves or joins the schema together with its wire type, and the hierarchy has
            // already said so by that name. Only its appearance and disappearance, though: a variant
            // that stays is compared field by field like any other definition.
            if ((now == null || old == null) && name in spoken) return@forEach

            when {
                now == null ->
                    changes +=
                        SchemaChange(
                            SchemaCompatibilityRules.TYPE_REMOVED,
                            Compatibility.BREAKING,
                            name,
                            "the definition is gone — §15: removal or rename of a type",
                        )

                old == null ->
                    changes +=
                        SchemaChange(
                            SchemaCompatibilityRules.DEFINITION_ADDED,
                            Compatibility.COMPATIBLE,
                            name,
                            "a definition nothing could reference before; what it costs is decided where a field " +
                                "starts naming it, and that shows up as a change of that field",
                        )

                else -> changes += changesIn(name, old, now)
            }
        }

        return changes
    }

    private fun changesIn(
        name: String,
        old: Definition,
        now: Definition,
    ): List<SchemaChange> {
        val changes = mutableListOf<SchemaChange>()

        if (old.kind != now.kind) {
            changes +=
                SchemaChange(
                    SchemaCompatibilityRules.DEFINITION_KIND_CHANGED,
                    Compatibility.UNCLASSIFIED,
                    name,
                    "\"${old.kind}\" became \"${now.kind}\" — §15 names no rule for a definition changing what it is",
                )
        }

        (old.properties.keys + now.properties.keys).sorted().forEach { field ->
            changes += fieldChanges("$name.$field", field, old, now)
        }

        // An enum is the type of every field that names it, so touching its values changes that type.
        // And it is the expensive direction: an unfamiliar constant fails the parse rather than
        // falling back to a default — the very reason §14 spends two fields on `maxLines` and
        // `ellipsis` instead of one mode enum.
        if (old.enumValues != null && now.enumValues != null) {
            (old.enumValues - now.enumValues.toSet()).forEach { value ->
                changes +=
                    SchemaChange(
                        SchemaCompatibilityRules.FIELD_TYPE_CHANGED,
                        Compatibility.BREAKING,
                        "$name/$value",
                        "an enum value is gone — §15: a change of a field's type, for every field of this enum",
                    )
            }
            (now.enumValues - old.enumValues.toSet()).forEach { value ->
                changes +=
                    SchemaChange(
                        SchemaCompatibilityRules.FIELD_TYPE_CHANGED,
                        Compatibility.BREAKING,
                        "$name/$value",
                        "a new enum value — §15: a change of a field's type. A reader released before it fails the " +
                            "parse on the unfamiliar constant instead of falling back to anything (§14)",
                    )
            }
        }

        if (old.additionalProperties != now.additionalProperties) {
            changes +=
                SchemaChange(
                    SchemaCompatibilityRules.UNKNOWN_FIELDS_CLOSED,
                    Compatibility.UNCLASSIFIED,
                    name,
                    "additionalProperties is ${now.additionalProperties} where it was ${old.additionalProperties} — " +
                        "§15 names no rule for it, while §3 is the promise that an unknown field is ignored",
                )
        }

        return changes
    }

    private fun fieldChanges(
        subject: String,
        field: String,
        old: Definition,
        now: Definition,
    ): List<SchemaChange> {
        val oldProperty = old.properties[field]
        val newProperty = now.properties[field]

        if (oldProperty == null) {
            return listOf(
                if (field in now.required) {
                    SchemaChange(
                        SchemaCompatibilityRules.REQUIRED_ADDED,
                        Compatibility.BREAKING,
                        subject,
                        "a new field, and a REQUIRED one — §15: adding a new field to required. A writer released " +
                            "before it sends a body the new reader rejects",
                    )
                } else {
                    SchemaChange(
                        SchemaCompatibilityRules.FIELD_ADDED_OPTIONAL,
                        Compatibility.COMPATIBLE,
                        subject,
                        "a new field outside required, i.e. one with a default — §15: compatible",
                    )
                },
            )
        }

        if (newProperty == null) {
            return listOf(
                SchemaChange(
                    SchemaCompatibilityRules.FIELD_REMOVED,
                    Compatibility.UNCLASSIFIED,
                    subject,
                    "the field is gone. §15 names neither its removal nor its cost: the body still parses, and a " +
                        "reader that showed the value now shows a default for ever",
                ),
            )
        }

        val changes = mutableListOf<SchemaChange>()

        if (canonical(oldProperty) != canonical(newProperty)) {
            val narrowed = isNullable(oldProperty) && !isNullable(newProperty)
            changes +=
                SchemaChange(
                    SchemaCompatibilityRules.FIELD_TYPE_CHANGED,
                    Compatibility.BREAKING,
                    subject,
                    if (narrowed) {
                        "nullable became non-nullable — §15: a change of a field's type, narrowing included"
                    } else {
                        "${canonical(oldProperty)} became ${canonical(newProperty)} — §15: a change of a field's type"
                    },
                )
        }

        val wasRequired = field in old.required
        val isRequired = field in now.required

        if (!wasRequired && isRequired) {
            changes +=
                SchemaChange(
                    SchemaCompatibilityRules.REQUIRED_ADDED,
                    Compatibility.BREAKING,
                    subject,
                    "an existing field entered required — §15: adding a new field to required",
                )
        }
        if (wasRequired && !isRequired) {
            changes +=
                SchemaChange(
                    SchemaCompatibilityRules.REQUIRED_REMOVED,
                    Compatibility.BREAKING,
                    subject,
                    "the field left required — §15: removal of a field from an object's required. A reader released " +
                        "before it still insists on receiving it",
                )
        }

        return changes
    }

    // ---- everything the rules above did not look at ----

    // THE TRIPWIRE. Every rule above was written against a change somebody has already made. The
    // generator is free to print a keyword none of them knows about tomorrow, and a checker built out
    // of rules alone would pass that change without a word. So what the rules do NOT model — the
    // definition minus its properties, its required list, its enum and its kind; the file minus its
    // $defs and its prose — is compared as text, and a difference nothing accounts for is reported as
    // a question instead of being dropped.
    private fun residualChanges(
        before: Model,
        after: Model,
    ): List<SchemaChange> {
        val changes = mutableListOf<SchemaChange>()

        before.definitions.forEach { (name, old) ->
            val now = after.definitions[name] ?: return@forEach
            if (old.residual == now.residual) return@forEach
            changes +=
                SchemaChange(
                    SchemaCompatibilityRules.UNCLASSIFIED_CHANGE,
                    Compatibility.UNCLASSIFIED,
                    name,
                    "the definition changed where no rule of §15 looks: " +
                        firstDifference(old.residual, now.residual),
                )
        }

        before.envelopes.forEach { (fileName, old) ->
            val now = after.envelopes[fileName] ?: return@forEach
            if (old == now) return@forEach
            changes +=
                SchemaChange(
                    SchemaCompatibilityRules.UNCLASSIFIED_CHANGE,
                    Compatibility.UNCLASSIFIED,
                    fileName,
                    "the file changed outside \$defs where no rule of §15 looks: " + firstDifference(old, now),
                )
        }

        return changes
    }

    private fun firstDifference(
        old: String,
        now: String,
    ): String {
        val at =
            old.zip(now).indexOfFirst { (a, b) -> a != b }.takeIf { it >= 0 }
                ?: minOf(old.length, now.length)
        val from = maxOf(0, at - 30)
        val oldTail = old.substring(minOf(from, old.length), minOf(old.length, at + 50))
        val newTail = now.substring(minOf(from, now.length), minOf(now.length, at + 50))
        return "\"…$oldTail…\" became \"…$newTail…\""
    }
}

// ---- the model both sides are read into ----

// What is known about one hierarchy, assembled from every file that says something about it: the
// module owning the open base, the modules contributing members to it, and the profile that closes
// the list for one build. Reading any of them alone gives the wrong answer — the profile says
// `x-kompot-open: false` of KompotComponent (that is what THIS BUILD emits) while the protocol leaves
// it open, and a member contributed by a module is invisible in the file that declares the base.
private data class Hierarchy(
    val open: Boolean,
    val degrades: Boolean?,
    val discriminator: String?,
    val members: Set<String>,
)

private data class Definition(
    val kind: String?,
    val wireType: String?,
    val properties: Map<String, JsonObject>,
    val required: Set<String>,
    val enumValues: List<String>?,
    val additionalProperties: JsonElement?,
    // The definition minus everything the rules model, as text. See the tripwire above.
    val residual: String,
)

private data class Model(
    val hierarchies: Map<String, Hierarchy>,
    val definitions: Map<String, Definition>,
    // One file minus its $defs, as text: the tripwire for a root keyword nobody here has met yet.
    val envelopes: Map<String, String>,
) {
    fun definitionOf(wireType: String): String? =
        definitions.entries.firstOrNull { it.value.wireType == wireType }?.key
}

// DEFINITIONS ARE KEYED BY NAME ACROSS THE WHOLE SET rather than by file#name. Which file a
// definition is printed into is packaging: the spec's own "Файлы схемы одинаковы у любой сборки" says
// a module's file does not depend on what is beside it, and a type moved from kompot-standard to
// kompot-core does not change the wire. Keying by file would report that move as a removal and an
// addition — twice wrong, and expensively so, because a removal is BREAKING.
private fun model(documents: Map<String, JsonObject>): Model {
    val hierarchies = mutableMapOf<String, Hierarchy>()
    val definitions = mutableMapOf<String, Definition>()
    val envelopes = mutableMapOf<String, String>()

    documents.toSortedMap().forEach { (fileName, document) ->
        envelopes[fileName] = canonical(JsonObject(document.filterKeys { it != DEFS }))

        // A module contributes members to a hierarchy whose base is declared elsewhere; that is the
        // only place where the addition of a type is visible without the profile.
        (document[CONTRIBUTES] as? JsonObject).orEmpty().forEach { (hierarchy, contributed) ->
            hierarchies[hierarchy] =
                merge(
                    hierarchies[hierarchy],
                    Hierarchy(
                        open = false,
                        degrades = null,
                        discriminator = null,
                        members = (contributed as? JsonObject).orEmpty().keys,
                    ),
                )
        }

        (document[DEFS] as? JsonObject).orEmpty().forEach { (name, element) ->
            val definition = element as? JsonObject ?: return@forEach

            if (definition.string(KIND) == HIERARCHY) {
                val discriminator = definition[DISCRIMINATOR] as? JsonObject
                hierarchies[name] =
                    merge(
                        hierarchies[name],
                        Hierarchy(
                            open = definition.boolean(OPEN) ?: false,
                            degrades = definition.boolean(DEGRADES),
                            discriminator = discriminator?.string("propertyName"),
                            members =
                                (discriminator?.get("mapping") as? JsonObject).orEmpty().keys +
                                    (definition[EXTENSIONS] as? JsonArray).orEmpty().map { it.primitive() },
                        ),
                    )
                return@forEach
            }

            definitions[name] =
                Definition(
                    kind = definition.string(KIND),
                    wireType = definition.string(WIRE_TYPE),
                    properties =
                        (definition["properties"] as? JsonObject)
                            .orEmpty()
                            .mapNotNull { (field, value) -> (value as? JsonObject)?.let { field to it } }
                            .toMap(),
                    required = (definition["required"] as? JsonArray).orEmpty().map { it.primitive() }.toSet(),
                    enumValues = (definition["enum"] as? JsonArray)?.map { it.primitive() },
                    additionalProperties = definition["additionalProperties"],
                    residual = canonical(JsonObject(definition.filterKeys { it !in MODELLED })),
                )
        }
    }

    return Model(hierarchies, definitions, envelopes)
}

private fun merge(
    existing: Hierarchy?,
    addition: Hierarchy,
): Hierarchy {
    if (existing == null) return addition

    // The module declaring the base OPEN is the one that gets to say whether it degrades: the profile
    // carries a copy of that flag, and a copy is not where a contradiction should be settled.
    val owner = if (addition.open && !existing.open) addition else existing
    val other = if (owner === existing) addition else existing

    return Hierarchy(
        open = existing.open || addition.open,
        degrades = owner.degrades ?: other.degrades,
        // An open base prints no discriminator — only the profile and the closed hierarchies do — so
        // the owner's `null` falls through to the one place that states it.
        discriminator = owner.discriminator ?: other.discriminator,
        members = existing.members + addition.members,
    )
}

// The text a comparison is made on: the same schema printed twice has to give the same string, and
// two schemas differing only in prose have to give one. Hence sorted keys (a JSON object is
// unordered), sorted arrays (`oneOf`, `required` and `enum` are sets in everything but syntax), prose
// dropped, and a $ref reduced to the definition it names — moving a definition between files rewrites
// every reference to it without changing what any of them point at.
private fun canonical(element: JsonElement): String =
    when (element) {
        is JsonObject ->
            element.entries
                .filterNot { it.key in IGNORED }
                .sortedBy { it.key }
                .joinToString(",", "{", "}") { (key, value) ->
                    "$key:" + if (key == "\$ref") canonicalReference(value.primitive()) else canonical(value)
                }

        is JsonArray -> element.map { canonical(it) }.sorted().joinToString(",", "[", "]")

        is JsonPrimitive -> if (element.isString) "\"${element.content}\"" else element.content
    }

private fun canonicalReference(reference: String) = "\"${reference.substringAfter("#")}\""

private fun isNullable(property: JsonObject): Boolean {
    val type = property["type"]
    if (type is JsonPrimitive && type.content == "null") return true
    if (type is JsonArray && type.any { (it as? JsonPrimitive)?.content == "null" }) return true

    val branches = (property["anyOf"] as? JsonArray) ?: (property["oneOf"] as? JsonArray) ?: return false
    return branches.any { it is JsonObject && isNullable(it) }
}

private const val DEFS = "\$defs"
private const val CONTRIBUTES = "x-kompot-contributes"
private const val EXTENSIONS = "x-kompot-extensions"
private const val KIND = "x-kompot-kind"
private const val OPEN = "x-kompot-open"
private const val DEGRADES = "x-kompot-degrades"
private const val WIRE_TYPE = "x-kompot-wire-type"
private const val DISCRIMINATOR = "discriminator"
private const val HIERARCHY = "hierarchy"

// What the rules above already read out of a definition, and therefore what the tripwire must not
// report a second time.
private val MODELLED = setOf(KIND, WIRE_TYPE, "properties", "required", "enum", "additionalProperties")

// Prose, addresses and the two root keys whose content is modelled as membership. A description is
// read by people and by nothing else (§3 has a reader ignore what it does not know); $id, $schema and
// title say where a file lives rather than what travels in it; x-kompot-contributes and
// x-kompot-modules are the hierarchies' membership, which the rules read as membership. Everything
// else counts.
private val IGNORED =
    setOf("description", "title", "\$id", "\$schema", "x-kompot-generated-by", CONTRIBUTES, "x-kompot-modules")

private fun JsonObject?.orEmpty(): Map<String, JsonElement> = this ?: emptyMap()

private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()

private fun JsonElement.primitive(): String = (this as JsonPrimitive).content

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.boolean(key: String): Boolean? = (this[key] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
