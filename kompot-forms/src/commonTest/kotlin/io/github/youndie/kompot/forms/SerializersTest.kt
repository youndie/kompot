package io.github.youndie.kompot.forms

import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.plus
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.kompotCoreSerializersModule
import io.github.youndie.kompot.generated.generatedFormsSerializersModule
import io.github.youndie.kompot.form.FormSchema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private val json =
    Json {
        classDiscriminator = "type"
        serializersModule = kompotCoreSerializersModule + kompotFormsSerializersModule + generatedFormsSerializersModule
    }

class SerializersTest {
    @Test
    fun `every form input component round-trips through the open KompotComponent type`() {
        val components: List<KompotComponent> =
            listOf(
                TextInputComponent(id = "1", fieldId = "search", label = "Search", uppercase = true),
                AmountInputComponent(id = "2", fieldId = "amount", label = "Amount", currencyFromField = "bucket"),
                CheckboxInputComponent(id = "3", fieldId = "auto", label = "Auto"),
                AutocompleteInputComponent(id = "4", fieldId = "b", label = "Recipient", dataSourceId = "search"),
                SelectInputComponent(id = "5", fieldId = "status", label = "Status", options = listOf(SelectOption("a", "A"))),
                RadioGroupComponent(id = "6", fieldId = "commission", label = "Surcharge", options = listOf(SelectOption("a", "A"))),
                ReadOnlyFieldComponent(id = "7", label = "Company", value = "TEBO"),
            )

        // The reified decodeFromString<T>() / encodeToString(component) do not resolve a serialiser
        // for a NON-sealed interface on Kotlin/Native: it fails on the iOS simulator target.
        val componentSerializer = PolymorphicSerializer(KompotComponent::class)
        components.forEach { component ->
            val decoded = json.decodeFromString(componentSerializer, json.encodeToString(componentSerializer, component))
            assertEquals(component, decoded)
        }
    }

    @Test
    fun `SubmitFormAction round-trips through the open KompotAction type`() {
        val action: KompotAction = SubmitFormAction(formId = "request_template")
        val actionSerializer = PolymorphicSerializer(KompotAction::class)

        val decoded = json.decodeFromString(actionSerializer, json.encodeToString(actionSerializer, action))
        assertEquals(action, decoded)
        assertIs<SubmitFormAction>(decoded)
    }

    @Test
    fun `SelectOption rawMetadata survives a round-trip and defaults to null`() {
        val withMetadata = SelectOption(id = "acc_1", label = "UZS", rawMetadata = mapOf("currency" to "UZS", "capacity" to "1000"))
        val decoded = json.decodeFromString<SelectOption>(json.encodeToString(withMetadata))
        assertEquals(withMetadata, decoded)

        val withoutMetadata = SelectOption(id = "acc_2", label = "USD")
        assertEquals(null, json.decodeFromString<SelectOption>(json.encodeToString(withoutMetadata)).rawMetadata)
    }

    @Test
    fun `KompotFormResponse round-trips schema and screen as one envelope`() {
        val response =
            KompotFormResponse(
                schema = FormSchema(formId = "form_1", fields = emptyList()),
                screen = TextInputComponent(id = "1", fieldId = "search", label = "Search"),
            )

        val decoded = json.decodeFromString<KompotFormResponse>(json.encodeToString(response))
        assertEquals(response.schema, decoded.schema)
        assertEquals(response.screen, decoded.screen)
    }
}
