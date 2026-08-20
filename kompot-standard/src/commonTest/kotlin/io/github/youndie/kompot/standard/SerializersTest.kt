package io.github.youndie.kompot.standard

import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.plus
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.UnknownComponent
import io.github.youndie.kompot.kompotCoreSerializersModule
import io.github.youndie.kompot.generated.generatedStandardSerializersModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private val json =
    Json {
        classDiscriminator = "type"
        serializersModule = kompotCoreSerializersModule + kompotStandardSerializersModule + generatedStandardSerializersModule
    }

// The reified decodeFromString<T>() / encodeToString<T>() do not resolve a serialiser for a
// NON-sealed interface on Kotlin/Native: it fails on the iOS simulator target while staying green on
// JVM. An explicit PolymorphicSerializer(T::class) behaves the same on every target.
private fun Json.encodeComponent(component: KompotComponent) = encodeToString(PolymorphicSerializer(KompotComponent::class), component)

private fun Json.decodeComponent(text: String) = decodeFromString(PolymorphicSerializer(KompotComponent::class), text)

private fun Json.encodeAction(action: KompotAction) = encodeToString(PolymorphicSerializer(KompotAction::class), action)

private fun Json.decodeAction(text: String) = decodeFromString(PolymorphicSerializer(KompotAction::class), text)

// Every type registered in kompotStandardSerializersModule must survive a JSON round trip with its
// "type" discriminator intact; otherwise the client gets UnknownComponent or UnknownAction instead
// of the real thing. A past bug did exactly that to the root of the tree.
class SerializersTest {
    @Test
    fun `ColumnComponent and RowComponent round-trip with nested children`() {
        val column = ColumnComponent(id = "col", children = listOf(TextComponent(id = "t", text = "hi")), spacing = 8)
        val encoded = json.encodeComponent(column)
        val decoded = json.decodeComponent(encoded)

        assertEquals(column, decoded)
        assertIs<ColumnComponent>(decoded)

        val row = RowComponent(id = "row", children = listOf(TextComponent(id = "t", text = "hi")))
        assertEquals(row, json.decodeComponent(json.encodeComponent(row)))
    }

    @Test
    fun `TextComponent and ButtonComponent round-trip including a polymorphic action`() {
        val text = TextComponent(id = "t", text = "hello")
        assertEquals(text, json.decodeComponent(json.encodeComponent(text)))

        val button = ButtonComponent(id = "b", text = "Go", action = NavigateAction(deeplink = "app://home"))
        val decoded = json.decodeComponent(json.encodeComponent(button))
        assertEquals(button, decoded)
        assertIs<ButtonComponent>(decoded)
        assertIs<NavigateAction>(decoded.action)
    }

    @Test
    fun `TableComponent round-trips header and data rows`() {
        val table =
            TableComponent(
                id = "table",
                rows = listOf(TableRow(listOf("A", "B"), header = true), TableRow(listOf("1", "2"))),
            )

        assertEquals(table, json.decodeComponent(json.encodeComponent(table)))
    }

    @Test
    fun `PaginatedListComponent round-trips its concretely-typed LoadPageAction fields`() {
        val list =
            PaginatedListComponent(
                id = "list",
                initialItems = listOf(TextComponent(id = "i1", text = "one")),
                loadMoreAction = LoadPageAction(url = "/items?page=2"),
                reloadUrl = "/items",
                emptyState = TextComponent(id = "empty", text = "none"),
            )

        val decoded = json.decodeComponent(json.encodeComponent(list))
        assertEquals(list, decoded)
    }

    @Test
    fun `NavigateAction — CopyTextAction — CloseAction and LoadPageAction all round-trip`() {
        val actions: List<KompotAction> =
            listOf(
                NavigateAction(deeplink = "app://home"),
                CopyTextAction(text = "1234567890"),
                CloseAction,
                LoadPageAction(url = "/items?page=3"),
            )

        actions.forEach { action ->
            val decoded = json.decodeAction(json.encodeAction(action))
            assertEquals(action, decoded)
        }
    }

    @Test
    fun `an unregistered component type falls back to UnknownComponent instead of throwing`() {
        val decoded = json.decodeComponent("""{"type":"video_player","id":"v1"}""")

        assertIs<UnknownComponent>(decoded)
        assertEquals("video_player", decoded.originalType)
    }

    @Test
    fun `the root of a tree keeps its discriminator when encoded through the open KompotComponent type`() {
        // A regression test for a real bug: the reflection-based serialisation path lost "type" on
        // the ROOT of the tree, because it resolved the serialiser of the concrete runtime class
        // directly instead of PolymorphicSerializer(KompotComponent::class). encodeComponent(value)
        // goes through the polymorphic serialiser and gets it right; the behaviour is pinned here
        // rather than left to "it just works".
        val root: KompotComponent = ColumnComponent(id = "root", children = emptyList())
        val encoded = json.encodeComponent(root)

        assertEquals(true, encoded.contains("\"type\":\"column\""))
    }
}
