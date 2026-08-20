package io.github.youndie.kompot.forms

import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.dsl.KompotContainerContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class RecordingContainer : KompotContainerContext {
    val children = mutableListOf<KompotComponent>()

    override fun addComponent(component: KompotComponent) {
        children.add(component)
    }

    fun single() = children.single()
}

class DslTest {
    @Test
    fun `textInput carries every field through and defaults uppercase to false`() {
        val container = RecordingContainer()
        container.textInput(fieldId = "search", label = "Search", placeholder = "enter text", mask = "###-##")

        val component = container.single() as TextInputComponent
        assertEquals("search", component.fieldId)
        assertEquals("Search", component.label)
        assertEquals("enter text", component.placeholder)
        assertEquals("###-##", component.mask)
        assertFalse(component.uppercase)
    }

    @Test
    fun `textInput honors an explicit id — otherwise generates one`() {
        val container = RecordingContainer()
        container.textInput(fieldId = "a", label = "A", id = "fixed-id")
        container.textInput(fieldId = "b", label = "B")

        val withId = container.children[0] as TextInputComponent
        val withoutId = container.children[1] as TextInputComponent
        assertEquals("fixed-id", withId.id)
        assertTrue(withoutId.id.isNotBlank())
        assertTrue(withoutId.id != "fixed-id")
    }

    @Test
    fun `amountInput defaults currencySuffix and currencyFromField to null`() {
        val container = RecordingContainer()
        container.amountInput(fieldId = "amount", label = "Amount")

        val component = container.single() as AmountInputComponent
        assertNull(component.currencySuffix)
        assertNull(component.currencyFromField)
    }

    @Test
    fun `amountInput carries currencyFromField through when provided`() {
        val container = RecordingContainer()
        container.amountInput(fieldId = "amount", label = "Amount", currencyFromField = "source_bucket")

        val component = container.single() as AmountInputComponent
        assertEquals("source_bucket", component.currencyFromField)
    }

    @Test
    fun `readOnlyField is not a form field — it only ever carries a static value`() {
        val container = RecordingContainer()
        container.readOnlyField(label = "Company", value = "TEBO STORE Ltd", helperText = "Reg. no. 123")

        val component = container.single() as ReadOnlyFieldComponent
        assertEquals("Company", component.label)
        assertEquals("TEBO STORE Ltd", component.value)
        assertEquals("Reg. no. 123", component.helperText)
    }

    @Test
    fun `checkboxInput carries fieldId and label through`() {
        val container = RecordingContainer()
        container.checkboxInput(fieldId = "auto_numbering", label = "Auto numbering")

        val component = container.single() as CheckboxInputComponent
        assertEquals("auto_numbering", component.fieldId)
        assertEquals("Auto numbering", component.label)
    }

    @Test
    fun `autocompleteInput requires a dataSourceId and carries it through`() {
        val container = RecordingContainer()
        container.autocompleteInput(fieldId = "beneficiary", label = "Recipient", dataSourceId = "beneficiaries_search")

        val component = container.single() as AutocompleteInputComponent
        assertEquals("beneficiaries_search", component.dataSourceId)
    }

    @Test
    fun `selectInput and radioGroup carry the same options list through unchanged`() {
        val options = listOf(SelectOption("a", "A"), SelectOption("b", "B", rawMetadata = mapOf("k" to "v")))

        val selectContainer = RecordingContainer()
        selectContainer.selectInput(fieldId = "status", label = "Status", options = options)
        val select = selectContainer.single() as SelectInputComponent
        assertEquals(options, select.options)

        val radioContainer = RecordingContainer()
        radioContainer.radioGroup(fieldId = "commission", label = "Surcharge", options = options)
        val radio = radioContainer.single() as RadioGroupComponent
        assertEquals(options, radio.options)
    }
}
