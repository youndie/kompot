package io.github.youndie.kompot.forms.standard

import io.github.youndie.kompot.forms.AmountInputComponent
import io.github.youndie.kompot.forms.AutocompleteInputComponent
import io.github.youndie.kompot.forms.CheckboxInputComponent
import io.github.youndie.kompot.forms.RadioGroupComponent
import io.github.youndie.kompot.forms.SelectInputComponent
import io.github.youndie.kompot.forms.SelectOption
import io.github.youndie.kompot.forms.TextInputComponent
import io.github.youndie.kompot.form.standard.AmountFieldDefinition
import io.github.youndie.kompot.form.standard.AutocompleteFieldDefinition
import io.github.youndie.kompot.form.standard.BooleanValue
import io.github.youndie.kompot.form.standard.CheckboxFieldDefinition
import io.github.youndie.kompot.form.standard.SelectionFieldDefinition
import io.github.youndie.kompot.form.standard.TextFieldDefinition
import io.github.youndie.kompot.form.standard.equals
import io.github.youndie.kompot.form.standard.required
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

// Every bound builder must draw EXACTLY one UI component AND add EXACTLY one field definition to the
// schema, both under the same fieldId. That is the "the UI and the schema cannot drift apart"
// contract these functions exist to guarantee.
class BoundFieldsTest {
    @Test
    fun `boundTextInput draws a TextInputComponent and registers a matching TextFieldDefinition`() {
        val response =
            buildFormScreen("form") {
                boundTextInput(fieldId = "search", label = "Search", uppercase = true) {
                    required("Required")
                }
            }

        val ui = uiChild<TextInputComponent>(response)
        assertEquals("search", ui.fieldId)
        assertTrue(ui.uppercase)

        val field = schemaField<TextFieldDefinition>(response)
        assertEquals("search", field.fieldId)
        assertEquals(1, field.rules.size)
        assertEquals("Required", field.rules.single().errorMessage)
    }

    @Test
    fun `boundAmountInput puts the currency on the UI component, where a renderer actually reads it`() {
        val response =
            buildFormScreen("form") {
                boundAmountInput(fieldId = "amount", label = "Amount", currencySuffix = "USD", currencyFromField = "source")
            }

        val ui = uiChild<AmountInputComponent>(response)
        assertEquals("USD", ui.currencySuffix)
        assertEquals("source", ui.currencyFromField)
    }

    @Test
    fun `boundCheckboxInput registers a CheckboxFieldDefinition alongside the checkbox`() {
        val response = buildFormScreen("form") { boundCheckboxInput(fieldId = "auto", label = "Auto") }

        uiChild<CheckboxInputComponent>(response)
        schemaField<CheckboxFieldDefinition>(response)
    }

    @Test
    fun `boundAutocompleteInput carries dataSourceId to the UI and registers an AutocompleteFieldDefinition`() {
        val response =
            buildFormScreen("form") {
                boundAutocompleteInput(fieldId = "beneficiary", label = "Recipient", dataSourceId = "beneficiaries_search")
            }

        assertEquals("beneficiaries_search", uiChild<AutocompleteInputComponent>(response).dataSourceId)
        assertEquals("beneficiaries_search", schemaField<AutocompleteFieldDefinition>(response).dataSourceId)
    }

    @Test
    fun `boundSelectInput and boundRadioGroup both back onto a SelectionFieldDefinition`() {
        val options = listOf(SelectOption("a", "A"))

        val selectResponse = buildFormScreen("form") { boundSelectInput(fieldId = "status", label = "Status", options = options) }
        assertEquals(options, uiChild<SelectInputComponent>(selectResponse).options)
        schemaField<SelectionFieldDefinition>(selectResponse)

        val radioResponse = buildFormScreen("form") { boundRadioGroup(fieldId = "commission", label = "Fee", options = options) }
        assertEquals(options, uiChild<RadioGroupComponent>(radioResponse).options)
        schemaField<SelectionFieldDefinition>(radioResponse)
    }

    @Test
    fun `visibleIf and triggersPatch reach the schema field even though the UI component has no such concept`() {
        val response =
            buildFormScreen("form") {
                boundTextInput(
                    fieldId = "gift_message",
                    label = "Message",
                    visibleIf = equals("is_gift", BooleanValue(true)),
                    triggersPatch = true,
                )
            }

        val field = schemaField<TextFieldDefinition>(response)
        assertTrue(field.triggersPatch)
        assertEquals(true, field.visibleIf?.evaluate { fieldId -> if (fieldId == "is_gift") BooleanValue(true) else null })
    }

    @Test
    fun `rules default to an empty list when no rules block is passed`() {
        val response = buildFormScreen("form") { boundTextInput(fieldId = "optional", label = "Optional") }

        assertFalse(schemaField<TextFieldDefinition>(response).rules.isNotEmpty())
    }

    @Test
    fun `bound fields inside a nested column still end up in the top-level schema`() {
        val response =
            buildFormScreen("form") {
                column {
                    boundTextInput(fieldId = "nested", label = "Nested")
                }
            }

        assertEquals(listOf("nested"), response.schema.fields.map { it.fieldId })
    }
}

private inline fun <reified T> uiChild(response: io.github.youndie.kompot.forms.KompotFormResponse): T =
    assertIs<T>((response.screen as io.github.youndie.kompot.standard.ColumnComponent).children.single())

private inline fun <reified T> schemaField(response: io.github.youndie.kompot.forms.KompotFormResponse): T =
    assertIs<T>(response.schema.fields.single())
