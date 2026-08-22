package io.github.youndie.kompot

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column as ComposeColumn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import io.github.youndie.kompot.forms.AmountInputComponent
import io.github.youndie.kompot.forms.AutocompleteInputComponent
import io.github.youndie.kompot.forms.CheckboxInputComponent
import io.github.youndie.kompot.forms.ReadOnlyFieldComponent
import io.github.youndie.kompot.forms.RadioGroupComponent
import io.github.youndie.kompot.forms.SelectInputComponent
import io.github.youndie.kompot.forms.TextInputComponent
import io.github.youndie.kompot.registry.KompotComponentMarker
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.form.collectFieldState
import io.github.youndie.kompot.form.collectVisibility
import io.github.youndie.kompot.form.standard.AmountValue
import io.github.youndie.kompot.form.standard.BooleanValue
import io.github.youndie.kompot.form.standard.EntityValue
import io.github.youndie.kompot.form.standard.TextValue

// The renderers of the :kompot-forms components. The package is deliberately the same one the core
// renderers live in, so a consumer's imports do not depend on which module a renderer sits in.

@KompotComponentMarker
class ReadOnlyFieldRenderer : KompotComponentRenderer<ReadOnlyFieldComponent> {
    @Composable
    override fun Render(
        component: ReadOnlyFieldComponent,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        OutlinedTextField(
            value = component.value,
            onValueChange = {},
            enabled = false,
            readOnly = true,
            label = { Text(component.label) },
            supportingText = component.helperText?.let { { Text(it) } },
            modifier = component.modifiers.toComposeModifier().fillMaxWidth(),
        )
    }
}

@KompotComponentMarker
class TextInputRenderer : KompotComponentRenderer<TextInputComponent> {
    @Composable
    override fun Render(
        component: TextInputComponent,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        // Reactive visibility: a field whose visibleIf does not hold is not drawn at all.
        val isVisible by formController.collectVisibility(component.fieldId)
        if (!isVisible) return

        // The typed state of this particular field.
        val fieldState by formController.collectFieldState<TextValue>(component.fieldId)
        val currentValue = fieldState.value?.text ?: ""

        val mask = component.mask
        val maskTransformation = remember(mask) { mask?.let { MaskVisualTransformation(it) } }

        // Validation runs on blur rather than on every keystroke.
        var wasFocused by remember(component.fieldId) { mutableStateOf(false) }

        OutlinedTextField(
            value = currentValue,
            onValueChange = { newValue ->
                // The stored value always stays raw: a mask is a visual layer only.
                val raw =
                    if (maskTransformation != null) {
                        newValue.filter { it.isDigit() }.take(maskTransformation.rawLength)
                    } else if (component.uppercase) {
                        // Codes are upper-cased in the stored value as well as on screen, not
                        // visually only.
                        newValue.uppercase()
                    } else {
                        newValue
                    }
                formController.onValueChanged(component.fieldId, TextValue(raw))
                // A field marked triggersPatch asks the backend for a patch asynchronously — an
                // autofill, typically; without it this does nothing.
                formController.requestPatchIfNeeded(component.fieldId)
            },
            label = { Text(component.label) },
            placeholder = component.placeholder?.let { { Text(it) } },
            isError = fieldState.changed && fieldState.error != null,
            supportingText = {
                if (fieldState.changed && fieldState.error != null) {
                    Text(fieldState.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                }
            },
            visualTransformation =
                when {
                    maskTransformation != null -> maskTransformation
                    component.secret -> PasswordVisualTransformation()
                    else -> VisualTransformation.None
                },
            keyboardOptions =
                if (maskTransformation != null) {
                    KeyboardOptions(keyboardType = KeyboardType.Number)
                } else {
                    KeyboardOptions.Default
                },
                // Only minLines, and singleLine is deliberately left as it was. Passing
                // `singleLine = !multiline` would have been the truer reading of the flag — and it
                // changed how every existing single-line field renders, which two screenshots caught.
                // Whether an ordinary field should stop wrapping is a behavioural decision for every
                // screen already drawn, not something to carry in on the back of a new field.
            minLines = if (component.multiline) MULTILINE_MIN_LINES else 1,
            modifier =
                component.modifiers.toComposeModifier().fillMaxWidth().onFocusChanged { focusState ->
                    if (wasFocused && !focusState.isFocused) {
                        formController.onFieldBlurred(component.fieldId)
                    }
                    wasFocused = focusState.isFocused
                },
        )
    }
}

@KompotComponentMarker
class AmountInputRenderer : KompotComponentRenderer<AmountInputComponent> {
    @Composable
    override fun Render(
        component: AmountInputComponent,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        // Reactive visibility: a field whose visibleIf does not hold is not drawn at all.
        val isVisible by formController.collectVisibility(component.fieldId)
        if (!isVisible) return

        val fieldState by formController.collectFieldState<AmountValue>(component.fieldId)
        // With currencyFromField set, the currency of the amount follows the EntityValue of that
        // neighbouring field locally, straight out of its rawMetadata. The data is already on the
        // client, so there is no reason to ask the server.
        val currencyFromField = component.currencyFromField
        if (currencyFromField != null) {
            val sourceState by formController.collectFieldState<EntityValue>(currencyFromField)

            LaunchedEffect(component.fieldId, sourceState.value) {
                val derivedCurrency = sourceState.value?.rawMetadata?.get("currency") ?: return@LaunchedEffect
                val current = formController.getTypedState<AmountValue>(component.fieldId)
                if (current.value?.currency != derivedCurrency) {
                    formController.onValueChanged(
                        component.fieldId,
                        AmountValue(current.value?.long ?: 0L, currency = derivedCurrency),
                    )
                }
            }
        }

        // A null value shows as an empty string.
        val currentValue = fieldState.value?.long?.toString() ?: ""
        // The currency of the value itself — switched by the derivation above or by a patch — wins
        // over the static one on the component.
        val currencySuffix = fieldState.value?.currency ?: component.currencySuffix

        // Validation runs on blur rather than on every keystroke.
        var wasFocused by remember(component.fieldId) { mutableStateOf(false) }

        OutlinedTextField(
            value = currentValue,
            onValueChange = { input ->
                // Everything but digits is stripped, which also survives a messy paste.
                val digitsOnly = input.filter { it.isDigit() }
                // A currency a patch already switched is not reset by ordinary typing.
                val currentCurrency = fieldState.value?.currency

                if (digitsOnly.isEmpty()) {
                    // Clearing the field resets the value to zero.
                    formController.onValueChanged(
                        component.fieldId,
                        AmountValue(0L, currency = currentCurrency),
                    )
                } else {
                    digitsOnly.toLongOrNull()?.let { amount ->
                        formController.onValueChanged(
                            component.fieldId,
                            AmountValue(amount, currency = currentCurrency),
                        )
                    }
                }
                formController.requestPatchIfNeeded(component.fieldId)
            },
            label = { Text(component.label) },
            isError = fieldState.changed && fieldState.error != null,
            supportingText = {
                if (fieldState.changed && fieldState.error != null) {
                    Text(fieldState.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                }
            },
            visualTransformation = remember(currencySuffix) { AmountVisualTransformation(currencySuffix) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier =
                component.modifiers.toComposeModifier().fillMaxWidth().onFocusChanged { focusState ->
                    if (wasFocused && !focusState.isFocused) {
                        formController.onFieldBlurred(component.fieldId)
                    }
                    wasFocused = focusState.isFocused
                },
        )
    }
}

@KompotComponentMarker
class CheckboxInputRenderer : KompotComponentRenderer<CheckboxInputComponent> {
    @Composable
    override fun Render(
        component: CheckboxInputComponent,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        // Reactive visibility: a field whose visibleIf does not hold is not drawn at all.
        val isVisible by formController.collectVisibility(component.fieldId)
        if (!isVisible) return

        val fieldState by formController.collectFieldState<BooleanValue>(component.fieldId)
        val isChecked = fieldState.value?.value ?: false

        Row(
            modifier =
                component.modifiers
                    .toComposeModifier()
                    .fillMaxWidth()
                    // The whole row is clickable, not just the box.
                    .clickable {
                        formController.onValueChanged(component.fieldId, BooleanValue(!isChecked))
                        formController.requestPatchIfNeeded(component.fieldId)
                    }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = isChecked,
                onCheckedChange = { checked ->
                    formController.onValueChanged(component.fieldId, BooleanValue(checked))
                    formController.requestPatchIfNeeded(component.fieldId)
                },
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = component.label)
        }
    }
}

private const val AUTOCOMPLETE_SEARCH_DEBOUNCE_MS = 500L

@KompotComponentMarker
class AutocompleteInputRenderer : KompotComponentRenderer<AutocompleteInputComponent> {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Render(
        component: AutocompleteInputComponent,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        // Reactive visibility: a field whose visibleIf does not hold is not drawn at all.
        val isVisible by formController.collectVisibility(component.fieldId)
        if (!isVisible) return

        val fieldState by formController.collectFieldState<EntityValue>(component.fieldId)

        // The text in the box is state of its own, independent of the FieldValue: while the user is
        // typing a query nothing is selected yet — an EntityValue appears only on choosing an option.
        var query by remember(component.fieldId) { mutableStateOf(fieldState.value?.title ?: "") }
        var results by remember(component.fieldId) { mutableStateOf<List<EntityValue>>(emptyList()) }
        var isSearching by remember(component.fieldId) { mutableStateOf(false) }
        var expanded by remember(component.fieldId) { mutableStateOf(false) }
        // Skips exactly the next run of LaunchedEffect(query): right after an option is chosen the
        // query is overwritten with its title programmatically, and searching for it again is
        // pointless.
        //
        // Comparing query with the selected title instead would not work: the field is synchronised on
        // every keystroke, so after the first selection that comparison would always hold and searching
        // would stay off forever.
        var skipNextSearch by remember(component.fieldId) { mutableStateOf(true) }
        // Validation waits until the field has actually been focused and then lost focus: otherwise
        // the very first render would light up an empty required field as an error.
        var wasFocused by remember(component.fieldId) { mutableStateOf(false) }

        // Debounced: the resolver is not asked on every character, only after a pause in typing.
        LaunchedEffect(component.fieldId, query) {
            if (skipNextSearch) {
                skipNextSearch = false
                return@LaunchedEffect
            }

            if (query.isBlank()) {
                results = emptyList()
                expanded = false
                return@LaunchedEffect
            }

            isSearching = true
            delay(AUTOCOMPLETE_SEARCH_DEBOUNCE_MS)
            results = formController.searchOptions(component.dataSourceId, query).filterIsInstance<EntityValue>()
            isSearching = false
            expanded = results.isNotEmpty()
        }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it && results.isNotEmpty() },
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { newQuery ->
                    query = newQuery
                    // Typing again means the previous selection no longer stands.
                    if (fieldState.value != null) {
                        formController.onValueChanged(component.fieldId, EntityValue(id = "", title = newQuery))
                    }
                },
                label = { Text(component.label) },
                placeholder = component.placeholder?.let { { Text(it) } },
                isError = fieldState.changed && fieldState.error != null,
                supportingText = {
                    if (fieldState.changed && fieldState.error != null) {
                        Text(fieldState.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    }
                },
                trailingIcon = {
                    if (isSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    }
                },
                modifier =
                    component.modifiers
                        .toComposeModifier()
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                        .onFocusChanged { focusState ->
                            if (wasFocused && !focusState.isFocused) {
                                formController.onFieldBlurred(component.fieldId)
                            }
                            wasFocused = focusState.isFocused
                        },
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                results.forEach { entity ->
                    DropdownMenuItem(
                        text = { Text(entity.title) },
                        onClick = {
                            skipNextSearch = true
                            query = entity.title
                            expanded = false
                            formController.onValueChanged(component.fieldId, entity)
                            // The autofill: with triggersPatch on the field, the server spreads the
                            // details of the chosen entity across the rest of the form itself.
                            formController.requestPatchIfNeeded(component.fieldId)
                        },
                    )
                }
            }
        }
    }
}

// A dropdown over a static list of options that arrived in the response itself: unlike the
// autocomplete, no search over the network. The value of the field is an EntityValue(id, label).
@KompotComponentMarker
class SelectInputRenderer : KompotComponentRenderer<SelectInputComponent> {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Render(
        component: SelectInputComponent,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        val isVisible by formController.collectVisibility(component.fieldId)
        if (!isVisible) return

        val fieldState by formController.collectFieldState<EntityValue>(component.fieldId)
        var expanded by remember(component.fieldId) { mutableStateOf(false) }
        // Validation waits until the field has actually been focused and then lost focus: otherwise
        // the very first render would light up an empty required field as an error.
        var wasFocused by remember(component.fieldId) { mutableStateOf(false) }

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = fieldState.value?.title ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text(component.label) },
                placeholder = component.placeholder?.let { { Text(it) } },
                isError = fieldState.changed && fieldState.error != null,
                supportingText = {
                    if (fieldState.changed && fieldState.error != null) {
                        Text(fieldState.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    }
                },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier =
                    component.modifiers
                        .toComposeModifier()
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .onFocusChanged { focusState ->
                            if (wasFocused && !focusState.isFocused) {
                                formController.onFieldBlurred(component.fieldId)
                            }
                            wasFocused = focusState.isFocused
                        },
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                component.options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            expanded = false
                            formController.onValueChanged(
                                component.fieldId,
                                EntityValue(option.id, option.label, option.rawMetadata),
                            )
                            formController.requestPatchIfNeeded(component.fieldId)
                        },
                    )
                }
            }
        }
    }
}

// A radio group: visually a different projection of the same "choose one of a fixed list" as the
// dropdown, down to the value type.
@KompotComponentMarker
class RadioGroupRenderer : KompotComponentRenderer<RadioGroupComponent> {
    @Composable
    override fun Render(
        component: RadioGroupComponent,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        val isVisible by formController.collectVisibility(component.fieldId)
        if (!isVisible) return

        val fieldState by formController.collectFieldState<EntityValue>(component.fieldId)
        val selectedId = fieldState.value?.id

        ComposeColumn(modifier = component.modifiers.toComposeModifier().fillMaxWidth()) {
            Text(text = component.label, style = MaterialTheme.typography.bodyMedium)
            component.options.forEach { option ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                formController.onValueChanged(
                                    component.fieldId,
                                    EntityValue(option.id, option.label, option.rawMetadata),
                                )
                                formController.requestPatchIfNeeded(component.fieldId)
                                formController.onFieldBlurred(component.fieldId)
                            }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = option.id == selectedId,
                        onClick = {
                            formController.onValueChanged(
                                component.fieldId,
                                EntityValue(option.id, option.label, option.rawMetadata),
                            )
                            formController.requestPatchIfNeeded(component.fieldId)
                            formController.onFieldBlurred(component.fieldId)
                        },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = option.label)
                }
            }
            if (fieldState.changed && fieldState.error != null) {
                Text(
                    fieldState.error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

// Enough room to show that more than one line is welcome, without deciding how much the text will
// need — the server states behaviour, the box still grows with the content.
private const val MULTILINE_MIN_LINES = 3
