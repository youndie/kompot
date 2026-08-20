package io.github.youndie.kompot.wizard

import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.kompotCoreSerializersModule
import io.github.youndie.kompot.generated.generatedStandardSerializersModule
import io.github.youndie.kompot.generated.generatedWizardSerializersModule
import io.github.youndie.kompot.standard.TextComponent
import io.github.youndie.kompot.form.FieldValue
import io.github.youndie.kompot.form.FormFieldDefinition
import io.github.youndie.kompot.form.FormSchema
import io.github.youndie.kompot.form.ValidationRule
import io.github.youndie.kompot.wizard.core.WizardTransition
import kotlin.test.Test
import kotlin.test.assertEquals

// A field definition and a field value of the test's own, rather than the ones from a concrete field
// plug-in: this module carries a FormSchema and a Map<String, FieldValue> through the wire without
// knowing a single concrete field type, and the test should not know one either. Anything else would
// give the module a dependency its production code does not have.
@Serializable
@SerialName("test_field")
private data class TestFieldDefinition(
    override val fieldId: String,
    override val rules: List<ValidationRule> = emptyList(),
) : FormFieldDefinition

@Serializable
@SerialName("test_value")
private data class TestValue(
    val text: String,
) : FieldValue

private val testFormModule =
    SerializersModule {
        polymorphic(FormFieldDefinition::class) { subclass(TestFieldDefinition::class) }
        polymorphic(FieldValue::class) { subclass(TestValue::class) }
    }

private val json =
    Json {
        classDiscriminator = "type"
        serializersModule =
            kompotCoreSerializersModule +
            generatedStandardSerializersModule +
            generatedWizardSerializersModule +
            kompotWizardSerializersModule +
            testFormModule
    }

// Explicit PolymorphicSerializer(...) rather than a reified decodeFromString<T>()/encodeToString(value):
// Kotlin/Native does not resolve reified generics for open (non-sealed) interfaces, the same class of
// bug that shows up wherever these types cross into Swift, and it surfaced here again once this module
// gained iOS targets.
private val kompotComponentSerializer = PolymorphicSerializer(KompotComponent::class)
private val kompotActionSerializer = PolymorphicSerializer(KompotAction::class)

class SerializersTest {
    @Test
    fun `WizardScreenComponent round-trips its content and progress metadata through the open KompotComponent type`() {
        val component: KompotComponent =
            WizardScreenComponent(
                id = "step",
                formId = "checkout",
                stepId = "details",
                stepIndex = 0,
                totalSteps = 3,
                canGoBack = false,
                content = TextComponent(id = "t", text = "Enter an amount"),
            )

        val decoded = json.decodeFromString(kompotComponentSerializer, json.encodeToString(kompotComponentSerializer, component))

        assertEquals(component, decoded)
    }

    @Test
    fun `NextStepAction PrevStepAction and FinishWizardAction round-trip through the open KompotAction type`() {
        for (action in listOf<KompotAction>(NextStepAction("checkout"), PrevStepAction("checkout"), FinishWizardAction("checkout"))) {
            val decoded = json.decodeFromString(kompotActionSerializer, json.encodeToString(kompotActionSerializer, action))
            assertEquals(action, decoded)
        }
    }

    @Test
    fun `WizardStepAction round-trips its schema and screen through the open KompotAction type`() {
        val action: KompotAction =
            WizardStepAction(
                formId = "checkout",
                schema = FormSchema(formId = "checkout", fields = listOf(TestFieldDefinition(fieldId = "full_name"))),
                screen =
                    WizardScreenComponent(
                        id = "step",
                        formId = "checkout",
                        stepId = "confirmation",
                        stepIndex = 2,
                        totalSteps = 3,
                        canGoBack = true,
                        content = TextComponent(id = "t", text = "Check your details"),
                    ),
            )

        val decoded = json.decodeFromString(kompotActionSerializer, json.encodeToString(kompotActionSerializer, action))

        assertEquals(action, decoded)
    }

    @Test
    fun `WizardResumeRequest round-trips its transition and typed field values`() {
        val request = WizardResumeRequest(transition = WizardTransition.Next, values = mapOf("full_name" to TestValue("Ada Lovelace")))

        val decoded = json.decodeFromString<WizardResumeRequest>(json.encodeToString(request))

        assertEquals(request, decoded)
    }
}
