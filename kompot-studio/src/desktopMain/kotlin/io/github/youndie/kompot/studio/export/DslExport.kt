package io.github.youndie.kompot.studio.export

import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.spec.KompotProtocol
import io.github.youndie.kompot.spec.KompotSpecResources
import io.github.youndie.kompot.studio.KompotStudioConfig
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.SerializersModuleCollector
import kotlin.reflect.KClass

// A DRAFT OF THE SERVER SIDE, printed from a body the studio has in front of it.
//
// The way in is the DSL — `kompotScreen { column { text(…) } }` — and the way back out has been a
// person retyping. The two are the same tree, so the retyping is work a machine can do; what a
// machine cannot do is know what the consumer's own components are called in Kotlin, because the
// schema does not carry class names. So this prints a draft: everything the toolkit ships comes out
// exact and compiles, and everything else comes out as a named guess with the guess marked.
//
// It is deliberately NOT a serializer — a generator whose output nobody edits is one, and the point
// here is Kotlin somebody takes over. But the Kotlin has to SAY what the body said: a field left out
// compiles, defaults, and is gone without a word, so the round trip is held by a test
// (DslExportRoundTripTest) even though nobody is meant to make it.
internal fun exportDsl(
    config: KompotStudioConfig,
    body: JsonElement,
    packageName: String? = null,
    functionName: String = "screen",
): String {
    val screen = screenOf(body) ?: return "// the body carries no screen to export"
    val writer = DslWriter(config)
    val call = writer.screen(screen)

    return buildString {
        packageName?.let { appendLine("package $it").appendLine() }
        writer.imports().forEach { appendLine("import $it") }
        if (writer.imports().isNotEmpty()) appendLine()
        appendLine("// Drafted from a JSON body by kompot-studio. Names marked TODO are guesses: the")
        appendLine("// schema carries wire types, and what a class is called in Kotlin is not on the wire.")
        appendLine("public fun ${identifier(functionName)}(): KompotComponent =")
        append(call.prependIndent("    "))
        appendLine()
    }
}

private fun screenOf(body: JsonElement): JsonObject? {
    val root = body as? JsonObject ?: return null
    if (root[KompotProtocol.DISCRIMINATOR] != null) return root
    // An envelope: the screen is one property in, and which one is the envelope's business rather
    // than a guess. `screen` is the name every shape in this toolkit uses for it.
    return root["screen"] as? JsonObject
}

private class DslWriter(
    private val config: KompotStudioConfig,
) {
    private val used = sortedSetOf<String>()

    // Which definitions belong to the toolkit, so a name this file GUESSES can be told from a name it
    // knows. Both sides guess the same way — `paginated_list` really is `PaginatedListComponent` —
    // but only one of them is checked by a compiler in this repository, and pretending otherwise
    // would put an unmarked wrong name in somebody's editor.
    private val toolkitFiles = KompotSpecResources("kompot-spec").schemas().keys

    // What each wire type is called in Kotlin, read off the Json the studio decodes with rather than
    // guessed from the wire name. The guess got the package wrong for every action outside
    // kompot-standard — `perform` is `io.github.youndie.kompot.commands.PerformAction`, `submit_form`
    // lives in kompot-forms — and the name wrong where the class is not the wire type in camel case
    // (`wizard_next` is `NextStepAction`). The Json holds the class the client decodes into, so a
    // draft that names it compiles wherever that client does.
    private val classes = kotlinClasses(config.json.serializersModule)

    fun imports(): List<String> = used.toList()

    fun screen(node: JsonObject): String {
        // `kompotScreen` IS a column builder, so a column root is the block itself rather than a
        // column inside one. Anything else keeps its own shape: wrapping a row in a column would
        // export a screen that is not the screen.
        if (wireType(node) != "column") return constructor(node)

        used += "io.github.youndie.kompot.KompotComponent"
        val id = stringOf(node["id"]) ?: ROOT
        val body = containerBody(node, id).prependIndent("    ")
        if (id == ROOT) {
            used += "io.github.youndie.kompot.standard.kompotScreen"
            return "kompotScreen {\n$body\n}"
        }
        // `kompotScreen` always names its column `root`. A body whose screen is called something else
        // keeps its name through the builder `kompotScreen` wraps, which numbers the children under it
        // the same way.
        used += "io.github.youndie.kompot.standard.ColumnBuilder"
        return "ColumnBuilder(id = ${quote(id)}).apply {\n$body\n}.build()"
    }

    private fun component(
        node: JsonObject,
        path: String?,
    ): String {
        val type = wireType(node) ?: return constructor(node)
        val modifiers = node["modifiers"] as? JsonArray
        // The DSL takes modifiers through a builder, and the builder cannot say `role` on a
        // background. Rather than drop it — which would export a screen that draws differently — the
        // node falls back to the constructor, where the list is written out exactly.
        if (!expressible(modifiers)) return constructor(node)

        val id = idArgument(node, path)
        return when (type) {
            "column", "row" -> {
                used += "io.github.youndie.kompot.standard.$type"
                val head = if (id == null) "$type {" else "$type($id) {"
                // A container's children are numbered under its own id — which is the path when it has
                // no name of its own, and its name when it has one.
                "$head\n" + containerBody(node, stringOf(node["id"]) ?: path.orEmpty()).prependIndent("    ") + "\n}"
            }

            "text" -> {
                used += "io.github.youndie.kompot.standard.text"
                call(
                    "text",
                    listOfNotNull(
                        string(node["text"]),
                        token(node, "style"),
                        token(node, "color"),
                        id,
                        flag(node, "heading", default = false),
                        int(node["maxLines"])?.let { "maxLines = $it" },
                        flag(node, "ellipsis", default = true),
                        spans(node["spans"]),
                        // Named: a lambda placed by position after named arguments lands on whatever
                        // parameter sits there, and the DSL keeps gaining parameters before this one.
                        modifierBlock(modifiers)?.let { "modifierBlock = $it" },
                    ),
                )
            }

            "button" -> {
                used += "io.github.youndie.kompot.standard.button"
                call(
                    "button",
                    listOfNotNull(
                        string(node["text"]),
                        action(node["action"]),
                        id,
                        stringOf(node["variant"])?.let { "variant = ${quote(it)}" },
                        stringOf(node["accessibilityLabel"])?.let { "accessibilityLabel = ${quote(it)}" },
                        modifierBlock(modifiers)?.let { "modifierBlock = $it" },
                    ),
                )
            }

            "table" -> {
                used += "io.github.youndie.kompot.standard.table"
                val arguments = listOfNotNull(id, modifierBlock(modifiers)?.let { "modifierBlock = $it" })
                val head = if (arguments.isEmpty()) "table {" else "table(${arguments.joinToString(", ")}) {"
                head + "\n" + rows(node).prependIndent("    ") + "\n}"
            }

            else -> {
                constructor(node)
            }
        }
    }

    // What a child looks like INSIDE a builder block. A DSL call adds itself; a constructor written
    // bare is an expression whose value falls on the floor — the draft would compile and the node
    // would never be on the screen, which is the worst kind of wrong for a file called a draft.
    private fun inBlock(
        node: JsonObject,
        path: String?,
    ): String {
        val printed = component(node, path)
        if (isDslCall(printed)) return printed
        // Only a TRAILING marker moves outside the call. Cutting at the first marker would cut inside
        // a nested list — the second guessed item and every closing parenthesis after it.
        val marked = printed.endsWith(MARKER)
        val call = if (marked) printed.removeSuffix(MARKER).trimEnd() else printed
        return "addComponent($call)" + if (marked) " $MARKER" else ""
    }

    private fun isDslCall(printed: String): Boolean =
        DSL_CALLS.any {
            printed.startsWith("$it(") ||
                printed.startsWith("$it {")
        }

    private fun containerBody(
        node: JsonObject,
        path: String,
    ): String {
        val lines = mutableListOf<String>()
        (node["spacing"] as? JsonPrimitive)
            ?.content
            ?.toIntOrNull()
            ?.takeIf { it != 0 }
            ?.let { lines += "spacing($it)" }
        modifierBlock(node["modifiers"] as? JsonArray)?.let { lines += "modifier $it" }
        // Open strings on the wire, printed as the words they are: a constant would name the same
        // word, and a word this toolkit has no constant for is still a word the DSL takes.
        stringOf(node["alignment"])?.let { lines += "alignment(${quote(it)})" }
        stringOf(node["arrangement"])?.let { lines += "arrangement(${quote(it)})" }
        if ((node["scrollable"] as? JsonPrimitive)?.booleanOrNull == true) lines += "scrollable()"
        action(node["action"])?.let { lines += "action($it)" }
        stringOf(node["accessibilityLabel"])?.let { lines += "accessibilityLabel(${quote(it)})" }
        (node["children"] as? JsonArray).orEmpty().forEachIndexed { index, child ->
            (child as? JsonObject)?.let { lines += inBlock(it, "$path/$index") }
        }
        return lines.joinToString("\n")
    }

    // A boolean printed only where it differs from the DSL's default, the way an id the DSL would
    // produce is left out.
    private fun flag(
        node: JsonObject,
        key: String,
        default: Boolean,
    ): String? = (node[key] as? JsonPrimitive)?.booleanOrNull?.takeIf { it != default }?.let { "$key = $it" }

    // Spans are plain objects with no discriminator, so the generic constructor path would print them
    // as a TODO; their class is known, and every field is named off the body like any constructor.
    private fun spans(element: JsonElement?): String? {
        val spans = (element as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        if (spans.isEmpty()) return null
        used += "io.github.youndie.kompot.standard.TextSpan"
        val printed =
            spans.map { span ->
                call("TextSpan", span.entries.map { (key, held) -> "$key = ${value(key, held)}" })
            }
        return "spans = listOf(" + printed.joinToString(", ") + ")"
    }

    private fun rows(node: JsonObject): String =
        (node["rows"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.joinToString("\n") { row ->
            val cells = (row["cells"] as? JsonArray).orEmpty().mapNotNull { string(it) }
            val header = (row["header"] as? JsonPrimitive)?.content == "true"
            call("row", cells + listOfNotNull(if (header) "header = true" else null))
        }

    // THE CONSTRUCTOR FORM, for everything the hand-written DSL has no call for: a consumer's own
    // component, a type whose modifiers the builder cannot express, an item inside a list that is
    // passed rather than nested. Named arguments throughout, read off the body, so what comes out
    // says the same thing the JSON did.
    private fun constructor(node: JsonObject): String {
        val type = wireType(node) ?: return "TODO(\"a node with no type\")"
        // A field value in a `perform` payload or an action in a `sequence` comes through here as well,
        // so the class is looked up for whatever the node is; the guess is for a type the Json does not
        // know, and then it is a guess at a component.
        val name = imported(type) ?: className(type, "Component")
        return call(name, arguments(type, node)) + if (known(type) || type in classes) "" else " $MARKER"
    }

    // The simple name of the class a wire type decodes into, with its import recorded; null when the
    // Json has no class for it, or more than one.
    private fun imported(wireType: String): String? {
        val qualified = classes[wireType]?.name ?: return null
        used += qualified
        return qualified.substringAfterLast('.')
    }

    // Named arguments read off the body, every field but the discriminator.
    private fun arguments(
        type: String,
        node: JsonObject,
    ): List<String> {
        val floats = floatProperties(type)
        val maps = mapProperties(type)
        return node.entries
            .filter { it.key != KompotProtocol.DISCRIMINATOR }
            .map { (key, held) -> "$key = ${value(key, held, floats, maps)}" }
    }

    // `update` through its own builder, `kompotUpdate { … }`, so the frames are written with the same
    // calls as the screen they replace nodes of. The builder addresses a frame by the id of its node,
    // so a frame whose `componentId` names another node — or a field the builder has no parameter
    // for — keeps the constructor, which says all of it.
    private fun update(node: JsonObject): String {
        val frames = (node["updates"] as? JsonArray).orEmpty()
        val built =
            node.keys.all { it in UPDATE_KEYS } &&
                listOf("deeplink", "history").all {
                    node[it] == null || node[it] is JsonNull ||
                        stringOf(node[it]) != null
                } &&
                frames.all { frame ->
                    val id = stringOf(((frame as? JsonObject)?.get("component") as? JsonObject)?.get("id"))
                    frame is JsonObject && frame.keys == FRAME_KEYS && id != null &&
                        stringOf(frame["componentId"]) == id
                }
        if (!built) {
            // The frame is a plain class rather than a polymorphic one, so the Json does not name it.
            used += UPDATE_FRAME
            val name = checkNotNull(imported(UPDATE))
            val arguments =
                node.entries.filter { it.key != KompotProtocol.DISCRIMINATOR }.map { (key, held) ->
                    if (key != "updates") return@map "$key = ${value(key, held)}"
                    "$key = listOf(" +
                        frames.joinToString(", ") { frame ->
                            if (frame !is JsonObject) return@joinToString "TODO(\"$key\")"
                            call(
                                UPDATE_FRAME.substringAfterLast('.'),
                                frame.entries.map { (k, v) ->
                                    "$k = ${value(k, v)}"
                                },
                            )
                        } + ")"
                }
            return call(name, arguments)
        }

        used += UPDATE_BUILDER
        val arguments =
            listOf("deeplink", "history").mapNotNull { key ->
                stringOf(node[key])?.let { "$key = ${quote(it)}" }
            }
        val head = if (arguments.isEmpty()) "kompotUpdate {" else "kompotUpdate(${arguments.joinToString(", ")}) {"
        // No path: every node of an update carries its id, and an id left out as "the one the DSL
        // would give" would be refused by the builder.
        val body = frames.joinToString("\n") { inBlock(it.jsonObject.getValue("component").jsonObject, null) }
        return if (body.isEmpty()) "$head\n}" else "$head\n" + body.prependIndent("    ") + "\n}"
    }

    private fun value(
        key: String,
        element: JsonElement,
        floats: Set<String> = emptySet(),
        maps: Set<String> = emptySet(),
    ): String =
        when {
            element is JsonNull -> {
                "null"
            }

            // A map on the wire is an object with no discriminator, and so is a nested class; only the
            // schema tells them apart. The values are printed under the property's name, never the
            // map's own keys — a payload key called `color` is not a colour token.
            key in maps && element is JsonObject -> {
                "mapOf(" +
                    element.entries.joinToString(", ") { (entry, held) -> "${quote(entry)} to ${value(key, held)}" } +
                    ")"
            }

            key in floats && element is JsonPrimitive && !element.isString -> {
                "${element.content}f"
            }

            key == "modifiers" && element is JsonArray -> {
                "listOf(" + element.joinToString(", ") { modifierNode(it) } +
                    ")"
            }

            key == "action" || key == "loadMoreAction" -> {
                action(element) ?: "null"
            }

            key == "style" -> {
                tokenValue("TypographyToken", element)
            }

            key == "color" -> {
                tokenValue("ColorToken", element)
            }

            element is JsonArray -> {
                "listOf(" + element.joinToString(", ") { value(key, it) } + ")"
            }

            element is JsonObject -> {
                if (element[KompotProtocol.DISCRIMINATOR] !=
                    null
                ) {
                    // An action nested in another — the steps of a `sequence` — is printed the way an
                    // action is, so a `data object` among them keeps its bare name.
                    val nested = wireType(element)?.let { classes[it] }
                    if (nested?.base == ACTION_BASE) action(element) ?: constructor(element) else constructor(element)
                } else {
                    "TODO(\"$key\")"
                }
            }

            else -> {
                primitive(element as JsonPrimitive)
            }
        }

    // Every modifier node the toolkit defines is a nested class, and printing them out longhand is
    // what makes the constructor form faithful where the builder is not.
    private fun modifierNode(element: JsonElement): String {
        val node = element as? JsonObject ?: return "TODO(\"a modifier that is not an object\")"
        val type = wireType(node) ?: return "TODO(\"a modifier with no type\")"
        used += "io.github.youndie.kompot.KompotModifierNode"
        val floats = floatProperties(type)
        val arguments =
            node.entries
                .filter { it.key != KompotProtocol.DISCRIMINATOR }
                .map { (key, held) ->
                    val printed =
                        when {
                            key == "color" -> {
                                tokenValue("ColorToken", held)
                            }

                            key == "colors" && held is JsonArray -> {
                                "listOf(" +
                                    held.joinToString(", ") { tokenValue("ColorToken", it) } +
                                    ")"
                            }

                            else -> {
                                value(key, held, floats)
                            }
                        }
                    "$key = $printed"
                }
        return call("KompotModifierNode." + camel(type), arguments)
    }

    private fun action(element: JsonElement?): String? {
        val node = element as? JsonObject ?: return null
        val type = wireType(node) ?: return null
        if (classes[type]?.name == UPDATE_ACTION) return update(node)
        // Nothing here knows what class that is, and a name invented for it would compile on some
        // deployments and not others. `TODO()` returns Nothing, so it compiles everywhere and stops.
        val name = imported(type) ?: return "TODO(\"$type\")"

        val arguments = arguments(type, node)
        // A type with nothing but its discriminator is a `data object` on this side, and an object
        // written with parentheses does not compile.
        return if (arguments.isEmpty()) name else call(name, arguments)
    }

    private fun modifierBlock(modifiers: JsonArray?): String? {
        val nodes = modifiers.orEmpty().mapNotNull { it as? JsonObject }
        if (nodes.isEmpty()) return null

        val calls =
            nodes.mapNotNull { node ->
                when (wireType(node)) {
                    // `all` is a per-side fallback rather than a fifth side, so writing it out as the
                    // four sides is the same padding and not an approximation.
                    "padding" -> {
                        val all = int(node["all"])
                        val sides =
                            listOf("top", "bottom", "start", "end")
                                .mapNotNull { side -> (int(node[side]) ?: all)?.let { "$side = $it" } }
                        if (sides.isEmpty()) null else call("padding", sides)
                    }

                    "background" -> {
                        "background(${tokenValue("ColorToken", node.getValue("color"))})"
                    }

                    "gradient" -> {
                        "gradientBackground(listOf(" +
                            (node["colors"] as? JsonArray).orEmpty().joinToString(
                                ", ",
                            ) { tokenValue("ColorToken", it) } +
                            "))"
                    }

                    "size" -> {
                        sizeCalls(node)
                    }

                    "weight" -> {
                        "weight(${(node["value"] as? JsonPrimitive)?.content}f)"
                    }

                    else -> {
                        null
                    }
                }
            }

        return if (calls.isEmpty()) null else "{\n" + calls.joinToString("\n").prependIndent("    ") + "\n}"
    }

    private fun sizeCalls(node: JsonObject): String =
        listOfNotNull(
            (node["width"] as? JsonPrimitive)?.content?.takeIf { it == "fill" }?.let { "fillMaxWidth()" },
            (node["height"] as? JsonPrimitive)?.content?.takeIf { it == "fill" }?.let { "fillMaxHeight()" },
            int(node["widthDp"])?.let { "width($it)" },
            int(node["heightDp"])?.let { "height($it)" },
            int(node["maxWidthDp"])?.let { "maxWidth($it)" },
            int(node["maxHeightDp"])?.let { "maxHeight($it)" },
        ).joinToString("\n")

    // Every modifier this toolkit has is expressible by the builder except one: a background carrying
    // a surface role. Naming the exception rather than testing for "did anything come out" keeps the
    // fallback narrow — a modifier list that came out empty because the writer forgot a case would
    // otherwise look exactly like one that had nothing to say.
    private fun expressible(modifiers: JsonArray?): Boolean =
        modifiers.orEmpty().mapNotNull { it as? JsonObject }.none {
            wireType(it) == "background" && it["role"] != null
        }

    private fun idArgument(
        node: JsonObject,
        path: String?,
    ): String? {
        val id = stringOf(node["id"]) ?: return null
        // An id the DSL would have produced by itself is left out: printing `id = "root/2"` beside
        // every call is noise, and it is noise that goes stale the moment somebody adds a node above.
        return if (id == path) null else "id = ${quote(id)}"
    }

    private fun token(
        node: JsonObject,
        key: String,
    ): String? {
        val element = node[key] ?: return null
        if (element is JsonNull) return null
        return "$key = ${tokenValue(if (key == "style") "TypographyToken" else "ColorToken", element)}"
    }

    private fun tokenValue(
        type: String,
        element: JsonElement,
    ): String {
        if (element is JsonNull) return "null"
        used += "io.github.youndie.kompot.$type"
        // A token is a value class over a plain string, so there is no constant name to guess at —
        // the key on the wire is the key in Kotlin.
        return "$type(${quote((element as JsonPrimitive).content)})"
    }

    // Which of a type's properties are Floats, read from the `format` the generator writes beside
    // `number`. Looked up by the discriminator's const across every definition rather than by a
    // definition key, so components, actions and modifier nodes are all answered the same way.
    private fun floatProperties(wireType: String): Set<String> =
        definitionsFor(wireType)
            .flatMap { definition ->
                (definition["properties"] as? JsonObject)
                    .orEmpty()
                    .entries
                    .filter { (_, schema) -> (schema.jsonObject["format"] as? JsonPrimitive)?.content == "float" }
                    .map { it.key }
            }.toSet()

    // Which of a type's properties are maps: an object whose `additionalProperties` is a schema rather
    // than a yes or no, which is how the generator writes a Map.
    private fun mapProperties(wireType: String): Set<String> =
        definitionsFor(wireType)
            .flatMap { definition ->
                (definition["properties"] as? JsonObject)
                    .orEmpty()
                    .entries
                    .filter { (_, schema) -> schema.jsonObject["additionalProperties"] is JsonObject }
                    .map { it.key }
            }.toSet()

    private fun definitionsFor(wireType: String): List<JsonObject> =
        config.schemas.values.flatMap { document ->
            (document["\$defs"] as? JsonObject).orEmpty().values.map { it.jsonObject }.filter { definition ->
                (definition["properties"] as? JsonObject)
                    ?.get(KompotProtocol.DISCRIMINATOR)
                    ?.jsonObject
                    ?.get("const")
                    ?.let { (it as? JsonPrimitive)?.content } == wireType
            }
        }

    private fun known(wireType: String): Boolean =
        config.schemas
            .filterKeys { it in toolkitFiles }
            .values
            .any { document ->
                (document["\$defs"] as? JsonObject)?.values.orEmpty().any { definition ->
                    (definition.jsonObject["properties"] as? JsonObject)
                        ?.get(KompotProtocol.DISCRIMINATOR)
                        ?.jsonObject
                        ?.get("const")
                        ?.let { (it as? JsonPrimitive)?.content } == wireType
                }
            }

    private fun className(
        wireType: String,
        suffix: String,
    ): String = camel(wireType).removeSuffix(suffix) + suffix

    private fun string(element: JsonElement?): String? = stringOf(element)?.let { quote(it) }

    private fun stringOf(element: JsonElement?): String? = (element as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun int(element: JsonElement?): Int? = (element as? JsonPrimitive)?.content?.toIntOrNull()

    private fun primitive(element: JsonPrimitive): String =
        if (element.isString) quote(element.content) else element.content

    private fun call(
        name: String,
        arguments: List<String>,
    ): String = if (arguments.isEmpty()) "$name()" else "$name(${arguments.joinToString(", ")})"

    private companion object {
        const val ROOT = "root"
        const val MARKER = "/* TODO: check this name */"

        // kompot-commands, named as text: the studio does not depend on the module, it only prints
        // calls into it. The witness draft (DslExportRoundTripTest) compiles them.
        const val UPDATE = "update"
        const val UPDATE_ACTION = "io.github.youndie.kompot.commands.UpdateAction"
        const val UPDATE_BUILDER = "io.github.youndie.kompot.commands.kompotUpdate"
        const val UPDATE_FRAME = "io.github.youndie.kompot.realtime.UpdateComponentMessage"
        val UPDATE_KEYS = setOf(KompotProtocol.DISCRIMINATOR, "updates", "deeplink", "history")
        val FRAME_KEYS = setOf("componentId", "component")
        val ACTION_BASE = KompotAction::class.qualifiedName
    }
}

// The class a wire type decodes into, and the open hierarchy it was registered under.
private data class KotlinClass(
    val name: String,
    val base: String?,
)

// Wire type → class, for every polymorphic registration in [module]. A wire type registered as two
// different classes (under two bases) is left out: the draft cannot know which one the body meant, and
// a wrong name is worse than the guess.
@OptIn(ExperimentalSerializationApi::class)
private fun kotlinClasses(module: SerializersModule): Map<String, KotlinClass> {
    val found = mutableMapOf<String, MutableSet<KotlinClass>>()
    module.dumpTo(
        object : SerializersModuleCollector {
            override fun <T : Any> contextual(
                kClass: KClass<T>,
                provider: (typeArgumentsSerializers: List<KSerializer<*>>) -> KSerializer<*>,
            ) = Unit

            override fun <Base : Any, Sub : Base> polymorphic(
                baseClass: KClass<Base>,
                actualClass: KClass<Sub>,
                actualSerializer: KSerializer<Sub>,
            ) {
                val name = actualClass.qualifiedName ?: return
                found.getOrPut(actualSerializer.descriptor.serialName) { mutableSetOf() } +=
                    KotlinClass(name, baseClass.qualifiedName)
            }

            override fun <Base : Any> polymorphicDefaultSerializer(
                baseClass: KClass<Base>,
                defaultSerializerProvider: (value: Base) -> SerializationStrategy<Base>?,
            ) = Unit

            override fun <Base : Any> polymorphicDefaultDeserializer(
                baseClass: KClass<Base>,
                defaultDeserializerProvider: (className: String?) -> DeserializationStrategy<Base>?,
            ) = Unit
        },
    )
    return found.filterValues { it.size == 1 }.mapValues { it.value.single() }
}

// The wire types printed as DSL calls rather than constructors — the ones whose fields the export has
// to carry by hand, and so the ones DslExportRoundTripTest holds it to.
internal val DSL_CALLS = setOf("column", "row", "text", "button", "table")

// A file is called `home-screen`; a function cannot be. Hyphens and anything else Kotlin refuses
// become camel-case joins, and a name that starts with a digit gets a letter in front — the draft is
// meant to compile before anybody has renamed anything.
internal fun identifier(name: String): String {
    val parts = name.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotEmpty() }
    val joined =
        parts
            .mapIndexed { index, part -> if (index == 0) part else part.replaceFirstChar { it.uppercaseChar() } }
            .joinToString("")
    val safe = joined.ifEmpty { "screen" }
    return if (safe.first().isDigit()) "screen$safe" else safe
}

private fun wireType(node: JsonObject): String? =
    (node[KompotProtocol.DISCRIMINATOR] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun camel(wireType: String): String =
    wireType.split('_').filter { it.isNotEmpty() }.joinToString("") { part ->
        part.replaceFirstChar { it.uppercaseChar() }
    }

private fun quote(text: String): String =
    "\"" +
        text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\$", "\\\$") + "\""

private fun <T> Collection<T>?.orEmpty(): Collection<T> = this ?: emptyList()

private fun JsonObject?.orEmpty(): Map<String, JsonElement> = this ?: emptyMap()

private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
