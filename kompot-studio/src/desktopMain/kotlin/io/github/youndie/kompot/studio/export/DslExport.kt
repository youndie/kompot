package io.github.youndie.kompot.studio.export

import io.github.youndie.kompot.spec.KompotProtocol
import io.github.youndie.kompot.spec.KompotSpecResources
import io.github.youndie.kompot.studio.KompotStudioConfig
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

// A DRAFT OF THE SERVER SIDE, printed from a body the studio has in front of it.
//
// The way in is the DSL — `kompotScreen { column { text(…) } }` — and the way back out has been a
// person retyping. The two are the same tree, so the retyping is work a machine can do; what a
// machine cannot do is know what the consumer's own components are called in Kotlin, because the
// schema does not carry class names. So this prints a draft: everything the toolkit ships comes out
// exact and compiles, and everything else comes out as a named guess with the guess marked.
//
// It is deliberately NOT a round trip. A body exported and re-imported would agree, but that is not
// what this is for — a generator whose output nobody edits is a serializer, and the point here is
// Kotlin somebody takes over.
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
        appendLine("public fun $functionName(): KompotComponent =")
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

    fun imports(): List<String> = used.toList()

    fun screen(node: JsonObject): String {
        // `kompotScreen` IS a column builder, so a column root is the block itself rather than a
        // column inside one. Anything else keeps its own shape: wrapping a row in a column would
        // export a screen that is not the screen.
        if (wireType(node) != "column") return constructor(node)

        used += "io.github.youndie.kompot.KompotComponent"
        used += "io.github.youndie.kompot.standard.kompotScreen"
        return "kompotScreen {\n" + containerBody(node, ROOT).prependIndent("    ") + "\n}"
    }

    private fun component(
        node: JsonObject,
        path: String,
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
                "$head\n" + containerBody(node, path).prependIndent("    ") + "\n}"
            }

            "text" -> {
                used += "io.github.youndie.kompot.standard.text"
                call("text", listOfNotNull(string(node["text"]), token(node, "style"), token(node, "color"), id, modifierBlock(modifiers)))
            }

            "button" -> {
                used += "io.github.youndie.kompot.standard.button"
                call("button", listOfNotNull(string(node["text"]), action(node["action"]), id, modifierBlock(modifiers)))
            }

            "table" -> {
                used += "io.github.youndie.kompot.standard.table"
                val arguments = listOfNotNull(id, modifierBlock(modifiers)?.let { "modifierBlock = $it" })
                val head = if (arguments.isEmpty()) "table {" else "table(${arguments.joinToString(", ")}) {"
                head + "\n" + rows(node).prependIndent("    ") + "\n}"
            }

            else -> constructor(node)
        }
    }

    private fun containerBody(
        node: JsonObject,
        path: String,
    ): String {
        val lines = mutableListOf<String>()
        (node["spacing"] as? JsonPrimitive)?.content?.toIntOrNull()?.takeIf { it != 0 }?.let { lines += "spacing($it)" }
        modifierBlock(node["modifiers"] as? JsonArray)?.let { lines += "modifier $it" }
        (node["children"] as? JsonArray).orEmpty().forEachIndexed { index, child ->
            (child as? JsonObject)?.let { lines += component(it, "$path/$index") }
        }
        return lines.joinToString("\n")
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
        val name = className(type, "Component")
        val arguments =
            node.entries
                .filter { it.key != KompotProtocol.DISCRIMINATOR }
                .map { (key, held) -> "$key = ${value(key, held)}" }

        return call(name, arguments) + if (known(type)) "" else " // TODO: check this name"
    }

    private fun value(
        key: String,
        element: JsonElement,
    ): String =
        when {
            element is JsonNull -> "null"
            key == "modifiers" && element is JsonArray -> "listOf(" + element.joinToString(", ") { modifierNode(it) } + ")"
            key == "action" || key == "loadMoreAction" -> action(element) ?: "null"
            key == "style" -> tokenValue("TypographyToken", element)
            key == "color" -> tokenValue("ColorToken", element)
            element is JsonArray -> "listOf(" + element.joinToString(", ") { value(key, it) } + ")"
            element is JsonObject -> if (element[KompotProtocol.DISCRIMINATOR] != null) constructor(element) else "TODO(\"$key\")"
            else -> primitive(element as JsonPrimitive)
        }

    // Every modifier node the toolkit defines is a nested class, and printing them out longhand is
    // what makes the constructor form faithful where the builder is not.
    private fun modifierNode(element: JsonElement): String {
        val node = element as? JsonObject ?: return "TODO(\"a modifier that is not an object\")"
        val type = wireType(node) ?: return "TODO(\"a modifier with no type\")"
        used += "io.github.youndie.kompot.KompotModifierNode"
        val arguments =
            node.entries
                .filter { it.key != KompotProtocol.DISCRIMINATOR }
                .map { (key, held) ->
                    val printed =
                        when {
                            key == "color" -> tokenValue("ColorToken", held)
                            key == "colors" && held is JsonArray -> "listOf(" + held.joinToString(", ") { tokenValue("ColorToken", it) } + ")"
                            else -> value(key, held)
                        }
                    "$key = $printed"
                }
        return call("KompotModifierNode." + camel(type), arguments)
    }

    private fun action(element: JsonElement?): String? {
        val node = element as? JsonObject ?: return null
        val type = wireType(node) ?: return null
        if (!known(type)) return "TODO(\"$type\")"

        val name = className(type, "Action")
        used += "io.github.youndie.kompot.standard.$name"
        val arguments =
            node.entries
                .filter { it.key != KompotProtocol.DISCRIMINATOR }
                .map { (key, held) -> "$key = ${value(key, held)}" }
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

                    "background" -> "background(${tokenValue("ColorToken", node.getValue("color"))})"
                    "gradient" ->
                        "gradientBackground(listOf(" +
                            (node["colors"] as? JsonArray).orEmpty().joinToString(", ") { tokenValue("ColorToken", it) } + "))"

                    "size" -> sizeCalls(node)
                    "weight" -> "weight(${(node["value"] as? JsonPrimitive)?.content}f)"
                    else -> null
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
        path: String,
    ): String? {
        val id = (node["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
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

    private fun string(element: JsonElement?): String? =
        (element as? JsonPrimitive)?.takeIf { it.isString }?.let { quote(it.content) }

    private fun int(element: JsonElement?): Int? = (element as? JsonPrimitive)?.content?.toIntOrNull()

    private fun primitive(element: JsonPrimitive): String = if (element.isString) quote(element.content) else element.content

    private fun call(
        name: String,
        arguments: List<String>,
    ): String = if (arguments.isEmpty()) "$name()" else "$name(${arguments.joinToString(", ")})"

    private companion object {
        const val ROOT = "root"
    }
}

private fun wireType(node: JsonObject): String? =
    (node[KompotProtocol.DISCRIMINATOR] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun camel(wireType: String): String =
    wireType.split('_').filter { it.isNotEmpty() }.joinToString("") { part ->
        part.replaceFirstChar { it.uppercaseChar() }
    }

private fun quote(text: String): String =
    "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\$", "\\\$") + "\""

private fun <T> Collection<T>?.orEmpty(): Collection<T> = this ?: emptyList()

private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
