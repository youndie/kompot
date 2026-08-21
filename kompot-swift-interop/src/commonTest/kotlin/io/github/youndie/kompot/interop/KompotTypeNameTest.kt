package io.github.youndie.kompot.interop

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotModifierNode
import io.github.youndie.kompot.kompotCoreSerializersModule
import kotlin.test.Test
import kotlin.test.assertEquals

// Types of the test's own rather than any concrete set: the function reads a SerialName out of the
// Json it was given, and knows no concrete type itself.
@Serializable
@SerialName("test_node")
private data class TypeNameNode(
    override val id: String,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

@Serializable
@SerialName("test_intent")
private data object TypeNameIntent : KompotAction

private val json =
    Json {
        classDiscriminator = "type"
        serializersModule =
            kompotCoreSerializersModule +
                SerializersModule {
                    polymorphic(KompotComponent::class) { subclass(TypeNameNode::class) }
                    polymorphic(KompotAction::class) { subclass(TypeNameIntent::class) }
                }
    }

class KompotTypeNameTest {
    @Test
    fun `resolves the SerialName of a concrete KompotComponent`() {
        assertEquals("test_node", kompotComponentTypeName(TypeNameNode(id = "t"), json))
    }

    @Test
    fun `resolves the SerialName of a concrete KompotAction`() {
        assertEquals("test_intent", kompotActionTypeName(TypeNameIntent, json))
    }
}
