package io.github.youndie.kompot.form.standard

import kotlinx.serialization.KSerializer
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import io.github.youndie.kompot.form.FieldValue
import io.github.youndie.kompot.form.FormCondition
import io.github.youndie.kompot.form.FormFieldDefinition
import io.github.youndie.kompot.form.ValidationRule
import kotlin.test.Test
import kotlin.test.assertEquals

private val json =
    Json {
        classDiscriminator = "type"
        serializersModule = formStandardSerializersModule
    }

// A reified decodeFromString<T>()/encodeToString<T>() does not resolve a serialiser for a non-sealed
// interface on Kotlin/Native — it fails on an iOS target while staying green on the JVM. The same
// class of problem that :kompot-swift-interop exists for.
private fun <T : Any> Json.roundTrip(
    serializer: KSerializer<T>,
    value: T,
): T = decodeFromString(serializer, encodeToString(serializer, value))

class FormStandardSerializersTest {
    @Test
    fun `every FieldValue type round-trips through the open FieldValue type`() {
        val values: List<FieldValue> =
            listOf(
                TextValue("hello"),
                AmountValue(15_000_00L, currency = "USD"),
                AmountValue(0L),
                BooleanValue(true),
                EntityValue(id = "src_1", title = "Source", rawMetadata = mapOf("currency" to "USD")),
                EntityValue(id = "", title = ""),
            )

        val serializer = PolymorphicSerializer(FieldValue::class)
        values.forEach { value ->
            assertEquals(value, json.roundTrip(serializer, value))
        }
    }

    @Test
    fun `every FormFieldDefinition type round-trips including its rules and visibleIf`() {
        val condition: FormCondition = EqualsCondition("auto_numbering", BooleanValue(true))
        val definitions: List<FormFieldDefinition> =
            listOf(
                TextFieldDefinition(fieldId = "phone", rules = listOf(RequiredRule("required")), visibleIf = condition),
                AmountFieldDefinition(fieldId = "amount", rules = emptyList()),
                CheckboxFieldDefinition(fieldId = "auto"),
                AutocompleteFieldDefinition(fieldId = "recipient", dataSourceId = "recipients_search"),
                SelectionFieldDefinition(fieldId = "status", triggersPatch = true),
            )

        val serializer = PolymorphicSerializer(FormFieldDefinition::class)
        definitions.forEach { definition ->
            assertEquals(definition, json.roundTrip(serializer, definition))
        }
    }

    @Test
    fun `every ValidationRule type round-trips through the open ValidationRule type`() {
        val rules: List<ValidationRule> =
            listOf(
                RequiredRule("required"),
                RegexRule(pattern = "^[A-Z0-9]{8}$", errorMessage = "invalid"),
                RequiredIfRule(targetFieldId = "is_gift", expectedValue = BooleanValue(true), errorMessage = "required"),
                MaxAmountRule(balanceFieldId = "source", errorMessage = "not enough left"),
            )

        val serializer = PolymorphicSerializer(ValidationRule::class)
        rules.forEach { rule ->
            assertEquals(rule, json.roundTrip(serializer, rule))
        }
    }

    @Test
    fun `EqualsCondition and NotEqualsCondition round-trip through the open FormCondition type`() {
        val conditions: List<FormCondition> =
            listOf(
                EqualsCondition("a", BooleanValue(true)),
                NotEqualsCondition("b", TextValue("x")),
            )

        val serializer = PolymorphicSerializer(FormCondition::class)
        conditions.forEach { condition ->
            assertEquals(condition, json.roundTrip(serializer, condition))
        }
    }
}
