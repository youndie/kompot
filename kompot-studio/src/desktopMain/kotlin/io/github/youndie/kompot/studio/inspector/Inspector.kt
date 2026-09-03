package io.github.youndie.kompot.studio.inspector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.youndie.kompot.material3.M3Colors
import io.github.youndie.kompot.material3.M3Typography
import io.github.youndie.kompot.studio.KompotStudioConfig
import io.github.youndie.kompot.studio.edit.JsonEdits
import io.github.youndie.kompot.studio.editor.lexJson
import io.github.youndie.kompot.studio.tree.ScreenNode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.RadioButtonRow
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField

// THE SELECTED NODE, AS FIELDS. Everything about the panel comes from the schema this build generates
// from its own types — which is what an inspector driven by a component registry is, except that the
// registry here was not written by hand.
@Composable
internal fun InspectorPane(
    config: KompotStudioConfig,
    node: ScreenNode?,
    body: String,
    modifier: Modifier = Modifier,
    onEdit: (String) -> Unit,
) {
    Column(
        modifier.padding(8.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (node == null) {
            Text("Select a node to see its properties.")
            return@Column
        }

        val fields = remember(config, node.wireType) { fieldsOf(config, node.wireType) }
        val values = remember(body, node.path) { valuesAt(body, node.path) }

        if (fields.isEmpty()) {
            // A type this build has no schema for. Its keys are still shown, and still editable as
            // text: a node nobody can describe is not a node nobody may touch.
            Text("This build's profile does not describe ${node.wireType}.")
        }

        fields.forEach { field ->
            PropertyRow(field, values[field.name]) { written ->
                val edited =
                    if (written == null) {
                        JsonEdits.removeProperty(body, node.path, field.name)
                    } else {
                        JsonEdits.setProperty(body, node.path, field.name, written)
                    }
                edited?.let(onEdit)
            }
        }

        // Whatever the node carries that the schema did not mention. Listed rather than dropped: the
        // text is the source of truth, and a panel that hid a key would be the one place claiming
        // otherwise.
        val described = fields.map { it.name }.toSet() + "type"
        values.keys.filterNot { it in described }.sorted().forEach { name ->
            Text("$name (not in this build's schema): ${values[name]}")
        }
    }
}

@Composable
private fun PropertyRow(
    field: PropertyField,
    raw: String?,
    onWrite: (String?) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // The sentence where somebody wrote one, and the name and shape where nobody did. A panel that
        // showed only the name would be a worse text editor.
        Text(field.name + if (field.required) " *" else "")
        field.description?.let { Text(it) }

        when (field.kind) {
            FieldKind.BOOLEAN ->
                CheckboxRow(
                    text = raw ?: "unset",
                    checked = raw == "true",
                    onCheckedChange = { onWrite(it.toString()) },
                )

            FieldKind.CHOICE ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (field.options.isEmpty()) {
                        Text("no values declared for this field")
                    } else {
                        field.options.forEach { option ->
                            RadioButtonRow(
                                text = option,
                                selected = raw?.trim('"') == option,
                                onClick = { onWrite("\"$option\"") },
                            )
                        }
                    }
                }

            // Text for everything else, INCLUDING what has no editor: a raw JSON value typed by hand
            // is worse than a control and better than a property somebody cannot reach.
            else -> {
                val state = remember(field.name, raw) { TextFieldState(raw.orEmpty()) }
                TextField(
                    state = state,
                    modifier = Modifier.fillMaxWidth(),
                    onKeyboardAction = { onWrite(state.text.toString().takeIf { it.isNotBlank() }) },
                )
            }
        }
    }
}

private fun fieldsOf(
    config: KompotStudioConfig,
    wireType: String,
): List<PropertyField> {
    val key = defKeyFor(config.schemas, wireType) ?: return emptyList()
    return fieldsFor(config.schemas, key, config.vocabulary[wireType].orEmpty(), tokenOptions(config))
}

// What a design-system key may be here: every token the kits name, plus the toolkit's own reference
// set, which resolves through the built-in design system and is therefore a real answer rather than a
// fallback.
private fun tokenOptions(config: KompotStudioConfig): Map<String, List<String>> {
    val colours =
        (config.themes.values.flatMap { it.light.colors.keys + it.dark?.colors?.keys.orEmpty() } +
            M3Colors.all.map { it.key }).distinct().sorted()
    val typography =
        (config.themes.values.flatMap { it.typography.keys } + M3Typography.all.map { it.key })
            .distinct()
            .sorted()

    return mapOf("ColorToken" to colours, "TypographyToken" to typography)
}

// The raw TEXT of each property, taken from the lexer rather than from a decoded object: what the
// panel edits is the document, and a value re-printed from a parsed tree would come back formatted
// differently from the way it was written.
private fun valuesAt(
    body: String,
    path: String,
): Map<String, String> {
    val node =
        runCatching { Json.parseToJsonElement(body) }.getOrNull()?.let { root ->
            (elementAt(root as? JsonObject, path))
        } ?: return emptyMap()

    val spans = lexJson(body).spans
    return node.keys.associateWith { name ->
        spans["$path.$name"]?.let { body.substring(it.first, it.last + 1) }.orEmpty()
    }
}

// The object a path names, walked the way the path was printed.
private fun elementAt(
    root: JsonObject?,
    path: String,
): JsonObject? {
    var current: kotlinx.serialization.json.JsonElement = root ?: return null
    if (path == "$") return current as? JsonObject

    path.removePrefix("$.").split('.').forEach { segment ->
        val name = segment.substringBefore('[')
        var next = (current as? JsonObject)?.get(name) ?: return null
        Regex("""\[(\d+)]""").findAll(segment).forEach { match ->
            next = (next as? kotlinx.serialization.json.JsonArray)?.get(match.groupValues[1].toInt()) ?: return null
        }
        current = next
    }
    return current as? JsonObject
}
