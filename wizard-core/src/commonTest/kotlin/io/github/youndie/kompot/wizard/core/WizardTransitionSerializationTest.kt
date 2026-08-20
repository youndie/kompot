package io.github.youndie.kompot.wizard.core

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

// A closed hierarchy (see the comment on WizardTransition): kotlinx.serialization generates the
// polymorphic serialiser for a sealed interface itself, with no registration in a SerializersModule.
private val json = Json { classDiscriminator = "type" }

class WizardTransitionSerializationTest {
    @Test
    fun `Next Back and Finish round-trip through the open WizardTransition type`() {
        val transitions: List<WizardTransition> = listOf(WizardTransition.Next, WizardTransition.Back, WizardTransition.Finish)
        for (transition in transitions) {
            val decoded = json.decodeFromString<WizardTransition>(json.encodeToString<WizardTransition>(transition))
            assertEquals(transition, decoded)
        }
    }

    @Test
    fun `JumpTo round-trips its stepId`() {
        // The type of the val is stated explicitly (WizardTransition, not WizardTransition.JumpTo) on
        // purpose: otherwise encodeToString(transition) resolves the serialiser of the CONCRETE subtype,
        // picked from the static type of the expression, instead of the polymorphic one, and the result
        // carries no "type" discriminator for decodeFromString<WizardTransition> to read.
        val transition: WizardTransition = WizardTransition.JumpTo("approval")
        val decoded = json.decodeFromString<WizardTransition>(json.encodeToString<WizardTransition>(transition))
        assertEquals(transition, decoded)
    }
}
