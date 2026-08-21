package io.github.youndie.kompot.analytics

import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotModifierNode
import kotlin.test.Test
import kotlin.test.assertEquals

private data class NamingTestComponent(
    override val id: String,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

private data class NamingTestAction(
    val target: String,
) : KompotAction

private data class UnregisteredComponent(
    override val id: String,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

private data class UnregisteredAction(
    val target: String,
) : KompotAction

class EventNamingRegistryTest {
    private val registry =
        KompotEventNamingRegistry(
            componentNaming =
                mapOf(
                    NamingTestComponent::class to
                        KompotComponentEventNaming { c -> KompotEventDescriptor("named_component", mapOf("id" to c.id)) },
                ),
            actionNaming =
                mapOf(
                    NamingTestAction::class to
                        KompotActionEventNaming { a -> KompotEventDescriptor("named_action", mapOf("target" to (a as NamingTestAction).target)) },
                ),
        )

    @Test
    fun `a registered component type returns its registered descriptor`() {
        val descriptor = registry.describe(NamingTestComponent(id = "c1"))

        assertEquals(KompotEventDescriptor("named_component", mapOf("id" to "c1")), descriptor)
    }

    @Test
    fun `a registered action type returns its registered descriptor`() {
        val descriptor = registry.describe(NamingTestAction(target = "/home"))

        assertEquals(KompotEventDescriptor("named_action", mapOf("target" to "/home")), descriptor)
    }

    @Test
    fun `an unregistered component type falls back to its class name and carries the component id`() {
        val descriptor = registry.describe(UnregisteredComponent(id = "c2"))

        assertEquals(KompotEventDescriptor("UnregisteredComponent", mapOf("componentId" to "c2")), descriptor)
    }

    @Test
    fun `an unregistered action type falls back to its class name with no properties`() {
        val descriptor = registry.describe(UnregisteredAction(target = "/x"))

        assertEquals(KompotEventDescriptor("UnregisteredAction"), descriptor)
    }

    @Test
    fun `an empty registry falls back for every component and action`() {
        val emptyRegistry = KompotEventNamingRegistry()

        assertEquals("NamingTestComponent", emptyRegistry.describe(NamingTestComponent(id = "c1")).eventName)
        assertEquals("NamingTestAction", emptyRegistry.describe(NamingTestAction(target = "/x")).eventName)
    }
}
