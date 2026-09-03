package io.github.youndie.kompot.studio.inspector

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import io.github.youndie.kompot.ColorToken
import io.github.youndie.kompot.TypographyToken
import io.github.youndie.kompot.material3.M3Colors
import io.github.youndie.kompot.material3.M3Typography
import io.github.youndie.kompot.studio.KompotStudioConfig
import io.github.youndie.kompot.studio.edit.JsonEdits
import io.github.youndie.kompot.studio.editor.lexJson
import io.github.youndie.kompot.studio.tree.ScreenNode
import io.github.youndie.kompot.studio.tree.iconFor
import io.github.youndie.kompot.studio.ui.Icon
import io.github.youndie.kompot.studio.ui.StudioIcon
import io.github.youndie.kompot.studio.ui.studioColors
import io.github.youndie.kompot.theme.KompotTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.jewel.ui.Outline
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.ListComboBox
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField

// THE SELECTED NODE, AS FIELDS. Everything about the panel comes from the schema this build generates
// from its own types — which is what an inspector driven by a component registry is, except that the
// registry here was not written by hand.
//
// Three properties get more than a text box because a text box is where a person without Kotlin
// gives up: `modifiers` is a table of the modifiers the toolkit has, an action is a form of its own
// type's fields, and a design-system key shows what it resolves to in the brand being looked at.
// `children` gets less — a line pointing at the tree, which is the editor for it.
@Composable
internal fun InspectorPane(
    config: KompotStudioConfig,
    node: ScreenNode?,
    body: String,
    brand: String?,
    dark: Boolean,
    modifier: Modifier = Modifier,
    onEdit: (String) -> Unit,
) {
    val colors = studioColors()
    Column(modifier.verticalScroll(rememberScrollState())) {
        if (node == null) {
            Text("Select a node to see its properties.", Modifier.padding(12.dp), color = colors.dim)
            return@Column
        }

        val fields = remember(config, node.wireType) { fieldsOf(config, node.wireType) }
        val values = remember(body, node.path) { valuesAt(body, node.path) }
        val theme = config.themes[brand] ?: config.themes.values.firstOrNull()
        val resolver = remember(theme, dark) { TokenResolver(theme, dark) }

        Row(
            Modifier.fillMaxWidth().height(28.dp).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Properties", fontWeight = FontWeight.Medium)
            Icon(iconFor(node), if (node.known) colors.dim else colors.warning)
            Text(node.wireType, color = colors.dim)
            node.id?.let { Mono(it, colors.dim) }
        }

        if (!node.known) {
            // A type this build has no schema for. Its keys are still shown, and still editable as
            // text: a node nobody can describe is not a node nobody may touch.
            Banner(
                "${node.wireType} isn't in this build's profile, so there is no schema for it. Properties are " +
                    "shown as JSON; the client draws a placeholder in its place.",
            )
        }

        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            fun write(name: String, written: String?) {
                val edited =
                    if (written == null) JsonEdits.removeProperty(body, node.path, name) else JsonEdits.setProperty(body, node.path, name, written)
                edited?.let(onEdit)
            }

            fields.forEach { field ->
                when {
                    field.name == "children" -> {
                        val count = node.children.size
                        LabelledRow("children", required = field.required, top = false) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("$count nodes — edit in Structure", color = colors.dim)
                                Icon(StudioIcon.NAVIGATE, colors.dim)
                            }
                        }
                    }

                    field.name == "modifiers" ->
                        LabelledRow("modifiers", required = field.required, top = true) {
                            ModifiersTable(config, values["modifiers"], resolver, onChange = { write("modifiers", it) })
                        }

                    field.kind == FieldKind.NESTED && field.hierarchy != null ->
                        LabelledRow(field.name, required = field.required, top = true) {
                            NestedForm(config, field, values[field.name], resolver) { write(field.name, it) }
                        }

                    else ->
                        LabelledRow(field.name, required = field.required, top = false) {
                            FieldEditor(field, values[field.name], resolver) { write(field.name, it) }
                        }
                }
            }

            // Whatever the node carries that the schema did not mention. Listed rather than dropped: the
            // text is the source of truth, and a panel that hid a key would be the one place claiming
            // otherwise.
            val described = fields.map { it.name }.toSet() + "type"
            val undescribed = values.keys.filterNot { it in described }.sorted()
            if (!node.known) {
                LabelledRow("id", required = true, top = false) {
                    FieldEditor(PropertyField("id", FieldKind.STRING, true, null), values["id"], resolver) { write("id", it) }
                }
                RawBlock(values.filterKeys { it != "id" && it != "type" })
            } else {
                undescribed.forEach { name ->
                    LabelledRow(name, required = false, top = false, dim = true) {
                        FieldEditor(PropertyField(name, FieldKind.RAW, false, "not in this build's schema"), values[name], resolver) { write(name, it) }
                    }
                }
            }
        }
    }
}

// One row of the grid: a label 88 wide, then the editor. `top` aligns the label with the first line
// of an editor that is taller than one — a table, a form.
@Composable
private fun LabelledRow(
    label: String,
    required: Boolean,
    top: Boolean,
    dim: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = studioColors()
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = if (top) Alignment.Top else Alignment.CenterVertically,
    ) {
        Row(Modifier.width(LABEL_WIDTH).padding(top = if (top) 5.dp else 0.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, color = if (dim) colors.dim else colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (required) Text("*", color = colors.dim)
        }
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun FieldEditor(
    field: PropertyField,
    raw: String?,
    resolver: TokenResolver,
    onWrite: (String?) -> Unit,
) {
    val colors = studioColors()
    when (field.kind) {
        FieldKind.BOOLEAN ->
            Checkbox(checked = raw == "true", onCheckedChange = { onWrite(it.toString()) })

        FieldKind.CHOICE ->
            when {
                field.options.isEmpty() -> Text("no values declared for this field", color = colors.dim)
                field.tokenKind == "ColorToken" -> TokenPicker(field, raw, resolver, onWrite)
                field.tokenKind == "TypographyToken" -> TokenPicker(field, raw, resolver, onWrite)
                else -> {
                    // A list, not a row of radio buttons: a typography scale has a dozen tokens, and
                    // a dozen buttons in the inspector's width squeezed the last of them to a column
                    // of letters. The first entry is "unset", because a property that is optional
                    // needs a way back to nothing.
                    val options = listOf(UNSET) + field.options
                    val current = raw?.trim('"')
                    ListComboBox(
                        items = options,
                        selectedIndex = options.indexOf(current).coerceAtLeast(0),
                        onSelectedItemChange = { index -> onWrite(if (index == 0) null else "\"${options[index]}\"") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

        FieldKind.NUMBER -> {
            val state = remember(field.name, raw) { TextFieldState(raw.orEmpty()) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    state = state,
                    modifier = Modifier.width(88.dp),
                    onKeyboardAction = { onWrite(state.text.toString().takeIf { it.isNotBlank() }) },
                )
                // Density-independent pixels are what every extent on the wire is measured in.
                if (field.name in DP_NAMES) Text("dp", color = colors.dim)
            }
        }

        // Text for everything else, INCLUDING what has no editor: a raw JSON value typed by hand
        // is worse than a control and better than a property somebody cannot reach.
        else -> {
            val state = remember(field.name, raw) { TextFieldState(raw.orEmpty()) }
            // Required and empty is the state a node arrives in from the palette, and the one thing
            // a text box cannot say by itself.
            val blank = field.required && (raw == null || raw == "\"\"")
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextField(
                    state = state,
                    modifier = Modifier.fillMaxWidth(),
                    outline = if (blank) Outline.Error else Outline.None,
                    onKeyboardAction = { onWrite(state.text.toString().takeIf { it.isNotBlank() }) },
                )
                if (blank) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(StudioIcon.ERROR, colors.error)
                        Text("Required — left empty.", color = colors.error, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// A DESIGN-SYSTEM KEY, with what it resolves to beside it: a swatch and the hex for a colour, a type
// sample and its metrics for a typography token — when the brand being looked at has a kit to
// answer from. A key alone is a name; a key with its value is something a person can choose.
@Composable
private fun TokenPicker(
    field: PropertyField,
    raw: String?,
    resolver: TokenResolver,
    onWrite: (String?) -> Unit,
) {
    val colors = studioColors()
    val current = raw?.trim('"')
    var open by remember { mutableStateOf(false) }
    val colour = field.tokenKind == "ColorToken"

    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .background(colors.field, RoundedCornerShape(6.dp))
                .border(1.dp, if (open) colors.accent else colors.controlLine, RoundedCornerShape(6.dp))
                .focusProperties { canFocus = false }
                .clickable { open = !open }
                .padding(start = 8.dp, end = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (current == null) {
                Text(UNSET, Modifier.weight(1f), color = colors.dim)
            } else {
                if (colour) Swatch(resolver.colour(current), colors) else TypeSample(colors)
                Mono(current, colors.text, Modifier.weight(1f))
                val detail = if (colour) resolver.hex(current) else resolver.metrics(current)
                if (detail != null) Mono(detail, colors.dim)
            }
            Icon(if (open) StudioIcon.CHEVRON_DOWN else StudioIcon.CHEVRON_DOWN, colors.dim)
        }

        if (open) {
            Popup(onDismissRequest = { open = false }) {
                Column(
                    Modifier
                        .width(POPUP_WIDTH)
                        .background(if (colors.field == Color.White) Color.White else Color(0xFF2B2D30), RoundedCornerShape(8.dp))
                        .border(1.dp, colors.controlLine, RoundedCornerShape(8.dp))
                        .padding(vertical = 6.dp),
                ) {
                    PopupRow(colors, selected = current == null, onClick = { onWrite(null); open = false }) {
                        Text(UNSET, color = colors.dim)
                    }
                    field.options.forEach { option ->
                        PopupRow(colors, selected = option == current, onClick = { onWrite("\"$option\""); open = false }) {
                            if (colour) Swatch(resolver.colour(option), colors) else TypeSample(colors)
                            Mono(option, colors.text, Modifier.weight(1f))
                            val detail = if (colour) resolver.hex(option) else resolver.metrics(option)
                            if (detail != null) Mono(detail, colors.dim)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PopupRow(
    colors: io.github.youndie.kompot.studio.ui.StudioColors,
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(26.dp)
            .background(if (selected) colors.selection else Color.Transparent)
            .focusProperties { canFocus = false }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun Swatch(
    colour: Color?,
    colors: io.github.youndie.kompot.studio.ui.StudioColors,
) {
    // A dashed square where the kit has no answer: the token is a name the client will look up, and
    // the studio says so rather than painting a guess.
    Box(
        Modifier
            .size(14.dp)
            .background(colour ?: Color.Transparent, RoundedCornerShape(3.dp))
            .border(1.dp, colors.controlLine, RoundedCornerShape(3.dp)),
    )
}

@Composable
private fun TypeSample(colors: io.github.youndie.kompot.studio.ui.StudioColors) {
    Text("Ag", Modifier.width(22.dp), color = colors.text, fontSize = 15.sp)
}

// THE MODIFIERS, as a table with a row per modifier and the controls its type has. What the table
// writes back is the whole list, printed compactly: a modifier is three numbers and a word, and the
// formatting a person chose for that is not worth the code that would preserve it.
@Composable
private fun ModifiersTable(
    config: KompotStudioConfig,
    raw: String?,
    resolver: TokenResolver,
    onChange: (String?) -> Unit,
) {
    val colors = studioColors()
    val modifiers = remember(raw) { parseModifiers(raw) }
    var adding by remember { mutableStateOf(false) }

    fun commit(list: List<JsonObject>) = onChange(if (list.isEmpty()) null else printModifiers(list))

    Column(Modifier.fillMaxWidth().border(1.dp, colors.line, RoundedCornerShape(6.dp))) {
        modifiers.forEachIndexed { index, modifier ->
            val type = modifier["type"]?.jsonPrimitive?.content ?: "?"
            Row(
                Modifier.fillMaxWidth().height(36.dp).padding(start = 8.dp, end = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(type, Modifier.width(72.dp), maxLines = 1)
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ModifierControls(config, modifier, resolver) { changed -> commit(modifiers.toMutableList().also { it[index] = changed }) }
                }
                Icon(
                    StudioIcon.REMOVE,
                    colors.dim,
                    Modifier.focusProperties { canFocus = false }.clickable { commit(modifiers.filterIndexed { i, _ -> i != index }) },
                )
            }
            Spacer(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
        }

        Box {
            Row(
                Modifier.fillMaxWidth().height(30.dp).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (modifiers.isEmpty()) Text("No modifiers", Modifier.weight(1f), color = colors.dim) else Spacer(Modifier.weight(1f))
                Row(
                    Modifier.focusProperties { canFocus = false }.clickable { adding = !adding },
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(StudioIcon.ADD, colors.text)
                    Text("Add")
                    Icon(StudioIcon.CHEVRON_DOWN, colors.dim)
                }
            }
            if (adding) {
                Popup(alignment = Alignment.TopEnd, onDismissRequest = { adding = false }) {
                    Column(
                        Modifier
                            .width(160.dp)
                            .background(if (colors.field == Color.White) Color.White else Color(0xFF2B2D30), RoundedCornerShape(8.dp))
                            .border(1.dp, colors.controlLine, RoundedCornerShape(8.dp))
                            .padding(vertical = 6.dp),
                    ) {
                        MODIFIER_TYPES.forEach { type ->
                            PopupRow(colors, selected = false, onClick = {
                                commit(modifiers + newModifier(type))
                                adding = false
                            }) { Text(type) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ModifierControls(
    config: KompotStudioConfig,
    modifier: JsonObject,
    resolver: TokenResolver,
    onChange: (JsonObject) -> Unit,
) {
    val colors = studioColors()
    fun int(name: String): Int? = (modifier[name] as? JsonPrimitive)?.content?.toIntOrNull()
    fun with(vararg pairs: Pair<String, JsonElement?>): JsonObject =
        buildJsonObject {
            modifier.forEach { (k, v) -> if (pairs.none { it.first == k }) put(k, v) }
            pairs.forEach { (k, v) -> if (v != null) put(k, v) }
        }

    when (modifier["type"]?.jsonPrimitive?.content) {
        // `all` is a per-side fallback on the wire, so the table shows the shorthand when only it is
        // set and the four sides otherwise — the same padding, spelled the way the body spells it.
        "padding" -> {
            val sides = listOf("top", "bottom", "start", "end")
            val bySides = sides.any { int(it) != null }
            SmallSegmented(listOf("All", "Sides"), if (bySides) "Sides" else "All") { picked ->
                if (picked == "All") {
                    onChange(with("all" to JsonPrimitive(int("all") ?: int("top") ?: 0), *sides.map { it to null }.toTypedArray()))
                } else {
                    val v = int("all") ?: 0
                    onChange(with("all" to null, *sides.map { it to JsonPrimitive(int(it) ?: v) }.toTypedArray()))
                }
            }
            if (bySides) {
                sides.forEach { side -> DpField(side.take(1).uppercase(), int(side) ?: 0) { onChange(with(side to JsonPrimitive(it))) } }
            } else {
                DpField(null, int("all") ?: 0) { onChange(with("all" to JsonPrimitive(it))) }
            }
        }

        "size" -> {
            SizeAxis("W", modifier["width"], int("widthDp")) { symbolic, dp ->
                onChange(with("width" to symbolic?.let(::JsonPrimitive), "widthDp" to dp?.let(::JsonPrimitive)))
            }
            SizeAxis("H", modifier["height"], int("heightDp")) { symbolic, dp ->
                onChange(with("height" to symbolic?.let(::JsonPrimitive), "heightDp" to dp?.let(::JsonPrimitive)))
            }
        }

        "weight" -> {
            val state = remember(modifier) { TextFieldState((modifier["value"] as? JsonPrimitive)?.content ?: "1.0") }
            TextField(state = state, modifier = Modifier.width(64.dp), onKeyboardAction = {
                state.text.toString().toFloatOrNull()?.let { onChange(with("value" to JsonPrimitive(it))) }
            })
        }

        "background" -> {
            val token = (modifier["color"] as? JsonPrimitive)?.content
            val options = remember(config) { tokenOptions(config)["ColorToken"].orEmpty() }
            Box(Modifier.weight(1f)) {
                TokenPicker(
                    PropertyField("color", FieldKind.CHOICE, true, null, options, tokenKind = "ColorToken"),
                    token?.let { "\"$it\"" },
                    resolver,
                ) { written -> onChange(with("color" to written?.let { JsonPrimitive(it.trim('"')) })) }
            }
            val role = remember(modifier) { TextFieldState((modifier["role"] as? JsonPrimitive)?.content.orEmpty()) }
            TextField(state = role, modifier = Modifier.width(78.dp), placeholder = { Text("role", color = colors.dim) }, onKeyboardAction = {
                onChange(with("role" to role.text.toString().takeIf { it.isNotBlank() }?.let(::JsonPrimitive)))
            })
        }

        else -> Mono(modifier.toString(), colors.dim, Modifier.weight(1f))
    }
}

@Composable
private fun SizeAxis(
    label: String,
    symbolic: JsonElement?,
    dp: Int?,
    onChange: (symbolic: String?, dp: Int?) -> Unit,
) {
    val colors = studioColors()
    val current = when {
        dp != null -> "dp"
        symbolic is JsonPrimitive -> symbolic.content
        else -> "wrap"
    }
    val options = listOf("fill", "wrap", "dp")
    Text(label, color = colors.dim, fontSize = 12.sp)
    ListComboBox(
        items = options,
        selectedIndex = options.indexOf(current).coerceAtLeast(0),
        onSelectedItemChange = { index ->
            when (options[index]) {
                "dp" -> onChange(null, dp ?: 100)
                else -> onChange(options[index], null)
            }
        },
        modifier = Modifier.width(84.dp),
    )
    if (dp != null) DpField(null, dp) { onChange(null, it) }
}

@Composable
private fun DpField(
    label: String?,
    value: Int,
    onChange: (Int) -> Unit,
) {
    val colors = studioColors()
    val state = remember(value) { TextFieldState(value.toString()) }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (label != null) Text(label, color = colors.dim, fontSize = 12.sp)
        TextField(state = state, modifier = Modifier.width(64.dp), onKeyboardAction = {
            state.text.toString().toIntOrNull()?.let(onChange)
        })
    }
}

@Composable
private fun SmallSegmented(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val colors = studioColors()
    Row(
        Modifier.border(1.dp, colors.controlLine, RoundedCornerShape(6.dp)).padding(1.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        options.forEach { option ->
            val on = option == selected
            Text(
                option,
                Modifier
                    .background(if (on) colors.hover else Color.Transparent, RoundedCornerShape(4.dp))
                    .focusProperties { canFocus = false }
                    .clickable { onSelect(option) }
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                fontSize = 12.sp,
            )
        }
    }
}

// AN ACTION, AS A FORM: its type from the hierarchy the profile declares, and the fields of that
// type below it — the same machinery as the node's own form, one level down.
@Composable
private fun NestedForm(
    config: KompotStudioConfig,
    field: PropertyField,
    raw: String?,
    resolver: TokenResolver,
    onWrite: (String?) -> Unit,
) {
    val colors = studioColors()
    val hierarchy = field.hierarchy ?: return
    val members = remember(config, hierarchy) { membersOf(config.schemas, hierarchy) }
    val current = remember(raw) { raw?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() } }
    val type = current?.get("type")?.jsonPrimitive?.content
    val subFields =
        remember(config, type) {
            type?.let { defKeyFor(config.schemas, it, hierarchy) }?.let { fieldsFor(config.schemas, it, tokens = tokenOptions(config)) }.orEmpty()
        }

    fun rewrite(typeName: String?, values: Map<String, JsonElement>) {
        if (typeName == null) return onWrite(null)
        onWrite(
            buildJsonObject {
                put("type", JsonPrimitive(typeName))
                values.forEach { (k, v) -> put(k, v) }
            }.toString().replace("\",\"", "\", \"").replace("{\"", "{ \"").replace("\"}", "\" }"),
        )
    }

    Column(Modifier.fillMaxWidth().border(1.dp, colors.line, RoundedCornerShape(6.dp)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("type", Modifier.width(72.dp), color = colors.dim)
            val options = listOf(UNSET) + members
            ListComboBox(
                items = options,
                selectedIndex = options.indexOf(type).coerceAtLeast(0),
                onSelectedItemChange = { index -> rewrite(if (index == 0) null else options[index], emptyMap()) },
                modifier = Modifier.weight(1f),
            )
        }
        subFields.forEach { sub ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.width(72.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(sub.name, color = colors.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (sub.required) Text("*", color = colors.dim)
                }
                Box(Modifier.weight(1f)) {
                    val subRaw = current?.get(sub.name)?.let { if (it is JsonPrimitive && it.isString) "\"${it.content}\"" else it.toString() }
                    FieldEditor(sub, subRaw, resolver) { written ->
                        val values = current.orEmpty().filterKeys { it != "type" && it != sub.name }.toMutableMap()
                        written?.let { text -> values[sub.name] = runCatching { Json.parseToJsonElement(text) }.getOrElse { JsonPrimitive(text) } }
                        rewrite(type, values)
                    }
                }
            }
        }
    }
}

@Composable
private fun Banner(text: String) {
    val colors = studioColors()
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .fillMaxWidth()
            .background(colors.warningBanner, RoundedCornerShape(6.dp))
            .border(1.dp, colors.warningBannerLine, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(StudioIcon.WARNING, colors.warning, Modifier.padding(top = 1.dp))
        Text(text, color = colors.text)
    }
}

@Composable
private fun RawBlock(values: Map<String, String>) {
    val colors = studioColors()
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.field, RoundedCornerShape(6.dp))
            .border(1.dp, colors.controlLine, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        values.forEach { (name, raw) -> Mono("\"$name\": $raw", colors.text) }
    }
}

@Composable
private fun Mono(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(text, modifier, color = color, fontFamily = FontFamily.Monospace, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

// What a key resolves to in the brand being looked at. Answered from the kit the configuration
// handed over; a deployment that keeps its kits in code hands nothing over, and then the picker
// shows names alone — which is what it had before.
private class TokenResolver(
    private val theme: KompotTheme?,
    private val dark: Boolean,
) {
    fun colour(token: String): Color? = theme?.colorFor(ColorToken(token), dark)?.let { Color(it) }

    fun hex(token: String): String? = theme?.colorFor(ColorToken(token), dark)?.let { "#%06X".format(it and 0xFFFFFF) }

    fun metrics(token: String): String? =
        theme?.styleFor(TypographyToken(token))?.let { style ->
            listOfNotNull(
                style.fontSizeSp?.let { it.toInt().toString() },
                style.lineHeightSp?.let { "/ ${it.toInt()}" },
                style.fontWeight?.let { "· $it" },
            ).joinToString(" ").ifEmpty { null }
        }
}

private fun parseModifiers(raw: String?): List<JsonObject> =
    raw?.let { runCatching { Json.parseToJsonElement(it) as? JsonArray }.getOrNull() }
        ?.mapNotNull { it as? JsonObject }
        .orEmpty()

private fun printModifiers(list: List<JsonObject>): String =
    buildJsonArray { list.forEach { add(it) } }
        .toString()
        .replace("\",\"", "\", \"")
        .replace("\":", "\": ")
        .replace("{\"", "{ \"")
        .replace("\"}", "\" }")
        .replace("},{", "}, {")
        .replace("[{", "[ {")
        .replace("}]", "} ]")

private fun newModifier(type: String): JsonObject =
    when (type) {
        "padding" -> buildJsonObject { put("type", JsonPrimitive("padding")); put("all", JsonPrimitive(16)) }
        "size" -> buildJsonObject { put("type", JsonPrimitive("size")); put("width", JsonPrimitive("fill")) }
        "weight" -> buildJsonObject { put("type", JsonPrimitive("weight")); put("value", JsonPrimitive(1.0)) }
        else -> buildJsonObject { put("type", JsonPrimitive("background")); put("color", JsonPrimitive("surface")) }
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
    var current: JsonElement = root ?: return null
    if (path == "$") return current as? JsonObject

    path.removePrefix("$.").split('.').forEach { segment ->
        val name = segment.substringBefore('[')
        var next = (current as? JsonObject)?.get(name) ?: return null
        Regex("""\[(\d+)]""").findAll(segment).forEach { match ->
            next = (next as? JsonArray)?.get(match.groupValues[1].toInt()) ?: return null
        }
        current = next
    }
    return current as? JsonObject
}

private val LABEL_WIDTH = 88.dp
private val POPUP_WIDTH = 320.dp
private val MODIFIER_TYPES = listOf("padding", "size", "weight", "background")
private val DP_NAMES = setOf("spacing", "padding", "widthDp", "heightDp", "maxWidthDp", "maxHeightDp")
private const val UNSET = "— unset —"
