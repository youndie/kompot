package io.github.youndie.kompot.standard

import io.github.youndie.kompot.ColorToken
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.TypographyToken
import io.github.youndie.kompot.generated.generatedStandardSerializersModule
import io.github.youndie.kompot.kompotCoreSerializersModule
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.modules.SerializersModuleCollector
import kotlinx.serialization.modules.plus
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Every field a standard component carries on the wire can be set from the DSL (#205).
//
// The 0.38 accessibility fields reached the data classes, the schema and the renderers, and not the
// DSL: a server written with kompotScreen { … } — the way the README writes one — could not mark a
// heading, and nothing said so. The build was green and the reader simply had no headings.
//
// So the question is asked of the wire itself rather than of a list kept by hand. For every component
// the generated serializers module registers, a witness is built through the DSL alone with each field
// set away from its default, and encoded without defaults: a field the DSL cannot reach stays at its
// default and is missing from the JSON. A field added to a data class tomorrow joins its descriptor,
// and this turns red until the DSL can set it. A component type added without a DSL call turns it red
// too, because it has no witness.
class DslReachesTheWireTest {
    private val json =
        Json {
            classDiscriminator = "type"
            encodeDefaults = false
            serializersModule =
                kompotCoreSerializersModule + kompotStandardSerializersModule + generatedStandardSerializersModule
        }

    @OptIn(ExperimentalSerializationApi::class)
    private fun registeredComponents(): Map<String, SerialDescriptor> {
        val found = mutableMapOf<String, SerialDescriptor>()
        generatedStandardSerializersModule.dumpTo(
            object : SerializersModuleCollector {
                override fun <T : Any> contextual(
                    kClass: KClass<T>,
                    provider: (typeArgumentsSerializers: List<KSerializer<*>>) -> KSerializer<*>,
                ) = Unit

                override fun <Base : Any, Sub : Base> polymorphic(
                    baseClass: KClass<Base>,
                    actualClass: KClass<Sub>,
                    actualSerializer: KSerializer<Sub>,
                ) {
                    if (baseClass == KompotComponent::class) {
                        found[actualSerializer.descriptor.serialName] = actualSerializer.descriptor
                    }
                }

                override fun <Base : Any> polymorphicDefaultSerializer(
                    baseClass: KClass<Base>,
                    defaultSerializerProvider: (value: Base) -> kotlinx.serialization.SerializationStrategy<Base>?,
                ) = Unit

                override fun <Base : Any> polymorphicDefaultDeserializer(
                    baseClass: KClass<Base>,
                    defaultDeserializerProvider: (
                        className: String?,
                    ) -> kotlinx.serialization.DeserializationStrategy<Base>?,
                ) = Unit
            },
        )
        return found
    }

    // One node of each type, built ONLY through the DSL, every field set to something other than its
    // default. Nothing here may be a constructor call: a constructor reaches every field by definition.
    private val witnesses: List<KompotComponent> =
        kompotScreen {
            column(id = "column") {
                modifier { padding(top = 4) }
                spacing(8)
                alignment(StackAlignment.CENTER)
                arrangement(StackArrangement.END)
                action(CloseAction)
                accessibilityLabel("Open the column")
                text("in the column")
            }
            row(id = "row") {
                modifier { padding(top = 4) }
                spacing(8)
                alignment(StackAlignment.CENTER)
                arrangement(StackArrangement.END)
                scrollable()
                action(CloseAction)
                accessibilityLabel("Open the row")
                text("in the row")
            }
            box(id = "box") {
                modifier { padding(top = 4) }
                alignment(BoxAlignment.CENTER)
                text("in the box")
            }
            tabs(id = "tabs", selected = 1) {
                modifier { padding(top = 4) }
                tab("First") { text("one") }
                tab("Second") { text("two") }
            }
            expandable(
                id = "expandable",
                expanded = true,
                modifierBlock = { padding(top = 4) },
                header = { text("Header") },
                content = { text("Content") },
            )
            divider(color = ColorToken("hairline"), id = "divider", modifierBlock = { padding(top = 4) })
            spacer(size = 8, weight = 1f, id = "spacer")
            text(
                "Title",
                style = TypographyToken("title"),
                color = ColorToken("ink"),
                id = "text",
                heading = true,
                maxLines = 2,
                ellipsis = false,
                spans = listOf(TextSpan("Title")),
                modifierBlock = { padding(top = 4) },
            )
            button(
                "Go",
                CloseAction,
                id = "button",
                variant = "quiet",
                accessibilityLabel = "Go on",
                modifierBlock = { padding(top = 4) },
            )
            table(id = "table", modifierBlock = { padding(top = 4) }) {
                row("Name", header = true)
                row("Value")
            }
            paginatedList(
                initialItems = emptyList(),
                loadMoreAction = LoadPageAction("/page/2"),
                reloadUrl = "/page/1",
                emptyState = TextComponent(id = "empty", text = "Nothing yet"),
                id = "paginated_list",
                modifierBlock = { padding(top = 4) },
            )
        }.children

    private fun encode(component: KompotComponent): JsonObject =
        json.encodeToJsonElement(PolymorphicSerializer(KompotComponent::class), component).jsonObject

    private fun SerialDescriptor.fieldNames(): Set<String> = (0 until elementsCount).map { getElementName(it) }.toSet()

    @Test
    fun `every field of every standard component can be set from the DSL`() {
        val registered = registeredComponents()
        // A positive control on the enumeration: an empty module would leave nothing to check and pass.
        assertTrue(
            registered.keys.containsAll(setOf("column", "row", "text", "button")),
            "registered: ${registered.keys}",
        )

        val encoded = witnesses.map(::encode).associateBy { it.getValue("type").toString().trim('"') }
        val gaps =
            registered.flatMap { (type, descriptor) ->
                val witness = encoded[type] ?: return@flatMap listOf("$type: no DSL call builds it")
                (descriptor.fieldNames() - witness.keys).map { "$type.$it" }
            }

        assertEquals(emptyList(), gaps.sorted(), "wire fields the DSL cannot set")
    }

    // The table's rows are built by its own builder, so the row's fields are the DSL's to reach too.
    @Test
    fun `every field of a table row can be set from the table builder`() {
        val table = encode(witnesses.single { it is TableComponent })
        val reached = (table.getValue("rows") as JsonArray).flatMap { it.jsonObject.keys }.toSet()

        assertEquals(TableRow.serializer().descriptor.fieldNames(), reached)
    }
}
