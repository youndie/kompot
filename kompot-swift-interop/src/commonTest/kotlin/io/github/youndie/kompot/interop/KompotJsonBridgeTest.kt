package io.github.youndie.kompot.interop

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotModifierNode
import io.github.youndie.kompot.kompotCoreSerializersModule
import kotlin.test.Test
import kotlin.test.assertEquals

// A component of the test's own rather than one from a concrete set: the bridge carries ANY tree of
// KompotComponent and knows no concrete type — the Json comes from outside, assembled by the
// application. The test should know no more than the production code does.
@Serializable
@SerialName("test_node")
private data class TestNode(
    override val id: String,
    val text: String,
    override val modifiers: List<KompotModifierNode> = emptyList(),
    val children: List<KompotComponent> = emptyList(),
) : KompotComponent

private val json =
    Json {
        classDiscriminator = "type"
        serializersModule =
            kompotCoreSerializersModule +
                SerializersModule { polymorphic(KompotComponent::class) { subclass(TestNode::class) } }
    }

class KompotJsonBridgeTest {
    @Test
    fun `a tree round-trips through the non-generic bridge Swift actually calls`() {
        val tree: KompotComponent =
            TestNode(id = "root", text = "hello", children = listOf(TestNode(id = "child", text = "world")))

        val decoded = decodeKompotComponent(json, encodeKompotComponent(json, tree))

        assertEquals(tree, decoded)
    }
}
