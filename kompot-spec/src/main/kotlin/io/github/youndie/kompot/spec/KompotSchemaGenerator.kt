package io.github.youndie.kompot.spec

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementDescriptors
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.SerializersModuleCollector
import kotlin.reflect.KClass

// The result of generating one schema file.
data class GeneratedSchema(
    val moduleName: String,
    val fileName: String,
    val document: JsonObject,
    // defKey -> file name: the index the following modules use to refer to an already described type
    // through a cross-file $ref instead of redefining it.
    val defKeys: Set<String>,
    // hierarchy -> (wire type -> defKey). The profile builds its closed oneOf/discriminator from this.
    val contributions: Map<String, Map<String, String>>,
)

// Walks the SerialDescriptors of the protocol types and prints JSON Schema 2020-12.
//
// Why a generator rather than a hand-written schema: any hand-written second source of truth drifts
// away from the Kotlin types sooner or later, and drifts silently. Here the schema is derived from the
// very descriptors kotlinx.serialization uses to encode a response, and the result is committed as a
// golden file — human-readable and reviewable, but unable to fall quietly behind the code.
@OptIn(ExperimentalSerializationApi::class)
class KompotSchemaGenerator(
    private val module: KompotSpecModule,
    // defKey -> the file a type is already described in. Modules are generated in order, the core
    // first.
    private val external: Map<String, String>,
) {
    private val defs = sortedMapOf<String, JsonObject>()
    private val contributions = sortedMapOf<String, MutableMap<String, String>>()

    fun generate(): GeneratedSchema {
        module.handWritten.forEach { (key, schema) -> defs[key] = schema }
        module.roots.forEach { root -> bareRef(root) }

        val dump = PolymorphicDump().also { module.serializersModule.dumpTo(it) }
        // dumpTo walks internal hash maps, so the order is not guaranteed between JVM runs; sorting
        // keeps the golden file byte-for-byte stable.
        dump.entries
            .sortedWith(compareBy({ it.hierarchy }, { it.wireName }))
            .forEach { entry ->
                val key = memberKey(entry.hierarchy, entry.wireName)
                defs[key] = classSchema(entry.descriptor, entry.wireName)
                contributions.getOrPut(entry.hierarchy) { sortedMapOf() }[entry.wireName] = key
            }

        applyAnnotations()

        return GeneratedSchema(
            moduleName = module.name,
            fileName = KompotProtocol.fileNameFor(module.name),
            document = document(),
            defKeys = defs.keys.toSet(),
            contributions = contributions.mapValues { (_, members) -> members.toMap() },
        )
    }

    // Constraints that cannot be derived from a Kotlin type: the format of a deeplink, the format of an
    // update topic, the reserved rawMetadata keys. In the type itself these are all just String, and the
    // convention lives in comments and in people's heads — which is exactly what another team's server
    // cannot guess.
    //
    // Every annotation must land on an existing property of an existing definition. A silently ignored
    // annotation is a constraint that looks described and applies to nothing, so this fails loudly:
    // rename a property and the build goes red instead of the spec growing a phantom rule.
    private fun applyAnnotations() {
        module.annotations.forEach { (defKey, annotatedProperties) ->
            val definition = defs[defKey] ?: error("Annotation in ${module.name}: no definition \"$defKey\"")
            val properties =
                definition["properties"] as? JsonObject
                    ?: error("Annotation in ${module.name}: definition \"$defKey\" has no properties")

            val patched = properties.toMutableMap()
            annotatedProperties.forEach { (property, extra) ->
                val existing =
                    properties[property] as? JsonObject
                        ?: error("Annotation in ${module.name}: \"$defKey\" has no property \"$property\"")
                patched[property] = JsonObject(existing + extra)
            }
            defs[defKey] = JsonObject(definition + mapOf("properties" to JsonObject(patched)))
        }
    }

    private fun document(): JsonObject =
        buildJsonObject {
            put("\$schema", KompotProtocol.SCHEMA_DIALECT)
            put("\$id", KompotProtocol.ID_PREFIX + KompotProtocol.fileNameFor(module.name))
            put("title", module.name)
            put("description", module.description)
            put("x-kompot-generated-by", "KompotSchemaGenerator — this file is generated, do not edit by hand")
            if (contributions.isNotEmpty()) {
                putJsonObject("x-kompot-contributes") {
                    contributions.forEach { (hierarchy, members) ->
                        putJsonObject(hierarchy) {
                            members.forEach { (wireName, key) -> put(wireName, "#/\$defs/$key") }
                        }
                    }
                }
            }
            putJsonObject("\$defs") {
                defs.forEach { (key, schema) -> put(key, schema) }
            }
        }

    // ---- named definitions -----------------------------------------------------------------

    // Returns a $ref to the definition of a type met as a concrete one (with no discriminator).
    private fun bareRef(descriptor: SerialDescriptor): JsonObject {
        val key = bareKey(descriptor.serialName)
        if (key !in external && key !in defs) {
            // A placeholder before the recursive walk: the component tree refers to itself (a column
            // holds children: List<KompotComponent>), and without it the descent would never end.
            defs[key] = JsonObject(emptyMap())
            defs[key] =
                when {
                    descriptor.isInline -> tokenSchema(descriptor)
                    descriptor.kind == SerialKind.ENUM -> enumSchema(descriptor)
                    descriptor.kind == PolymorphicKind.SEALED -> sealedSchema(key, descriptor)
                    else -> classSchema(descriptor, wireName = null)
                }
        }
        return ref(key)
    }

    private fun ref(key: String): JsonObject =
        buildJsonObject {
            val file = external[key]
            put("\$ref", if (file == null) "#/\$defs/$key" else "$file#/\$defs/$key")
        }

    // A value class over String (ColorToken/TypographyToken) is just a string on the wire. An open key
    // rather than an enum: the set of slots is defined by the client's design system, not by the
    // protocol, and a client MUST have a fallback for an unknown key (see SPEC.md).
    private fun tokenSchema(descriptor: SerialDescriptor): JsonObject {
        val underlying = primitiveSchema(descriptor.getElementDescriptor(0).kind)
        return buildJsonObject {
            put("x-kompot-kind", "token")
            put("description", "An open design-system string key (${descriptor.serialName.withoutNullMark()})")
            underlying.forEach { (key, value) -> put(key, value) }
        }
    }

    private fun enumSchema(descriptor: SerialDescriptor): JsonObject =
        buildJsonObject {
            put("x-kompot-kind", "enum")
            put("type", "string")
            put(
                "enum",
                buildJsonArray {
                    for (i in 0 until descriptor.elementsCount) add(JsonPrimitive(descriptor.getElementName(i)))
                },
            )
        }

    // A closed (sealed) hierarchy: unlike KompotComponent/KompotAction, no type can be added to it from
    // the server side — a client physically cannot parse one. Hence x-kompot-open: false, a normative
    // constraint on any server implementation (see SPEC.md).
    private fun sealedSchema(
        key: String,
        descriptor: SerialDescriptor,
    ): JsonObject {
        val members =
            descriptor
                .getElementDescriptor(1)
                .elementDescriptors
                .sortedBy { it.serialName }

        members.forEach { member ->
            defs[memberKey(key, member.serialName)] = classSchema(member, member.serialName)
        }

        return buildJsonObject {
            put("x-kompot-kind", "hierarchy")
            put("x-kompot-open", false)
            put(
                "oneOf",
                JsonArray(members.map { ref(memberKey(key, it.serialName)) }),
            )
            putJsonObject("discriminator") {
                put("propertyName", KompotProtocol.DISCRIMINATOR)
                putJsonObject("mapping") {
                    members.forEach { member ->
                        put(member.serialName, "#/\$defs/${memberKey(key, member.serialName)}")
                    }
                }
            }
        }
    }

    // wireName != null means the type was met as a member of a polymorphic hierarchy, so on the wire it
    // carries a discriminator. wireName == null means the same class was used as a concrete field type,
    // and then there is NO discriminator — kotlinx does not write one. A real example of the difference:
    // LoadPageAction inside paginated_list.loadMoreAction travels without "type", while the same class
    // as a @Polymorphic KompotAction travels with "type": "load_page". Hence two schema definitions.
    private fun classSchema(
        descriptor: SerialDescriptor,
        wireName: String?,
    ): JsonObject {
        val required = mutableListOf<String>()

        val properties =
            buildJsonObject {
                if (wireName != null) {
                    required += KompotProtocol.DISCRIMINATOR
                    putJsonObject(KompotProtocol.DISCRIMINATOR) { put("const", wireName) }
                }
                for (i in 0 until descriptor.elementsCount) {
                    val name = descriptor.getElementName(i)
                    if (!descriptor.isElementOptional(i)) required += name
                    put(name, propertySchema(descriptor.getElementDescriptor(i)))
                }
            }

        return buildJsonObject {
            put("x-kompot-kind", if (wireName != null) "variant" else "object")
            if (wireName != null) put("x-kompot-wire-type", wireName)
            put("type", "object")
            put("properties", properties)
            put("required", JsonArray(required.map { JsonPrimitive(it) }))
            // Not false: clients read with ignoreUnknownKeys = true, and that is part of the
            // compatibility contract — a newer server may send a property an older client does not know
            // yet (see SPEC.md, "Evolution").
            put("additionalProperties", true)
        }
    }

    // ---- properties ------------------------------------------------------------------------

    private fun propertySchema(descriptor: SerialDescriptor): JsonObject {
        val schema =
            when {
                descriptor.isInline -> bareRef(descriptor)
                descriptor.kind == SerialKind.ENUM -> bareRef(descriptor)
                descriptor.kind is PrimitiveKind -> primitiveSchema(descriptor.kind)
                descriptor.kind == StructureKind.LIST ->
                    buildJsonObject {
                        put("type", "array")
                        put("items", propertySchema(descriptor.getElementDescriptor(0)))
                    }

                descriptor.kind == StructureKind.MAP ->
                    buildJsonObject {
                        put("type", "object")
                        put("additionalProperties", propertySchema(descriptor.getElementDescriptor(1)))
                    }

                // An open hierarchy: refer to the base schema rather than to a list of variants — the
                // closed list is assembled only in the profile.
                descriptor.kind == PolymorphicKind.OPEN -> ref(openHierarchyName(descriptor.serialName))
                descriptor.kind == PolymorphicKind.SEALED -> bareRef(descriptor)
                descriptor.kind == StructureKind.CLASS || descriptor.kind == StructureKind.OBJECT -> bareRef(descriptor)
                else -> error("Unsupported SerialKind ${descriptor.kind} on ${descriptor.serialName}")
            }

        return if (descriptor.isNullable) nullable(schema) else schema
    }

    // In JSON Schema 2020-12 other keywords beside a $ref do apply, but anyOf reads less ambiguously
    // and survives code generators better than "type": [..., "null"] next to a $ref.
    private fun nullable(schema: JsonObject): JsonObject =
        if (schema.containsKey("\$ref")) {
            buildJsonObject {
                put(
                    "anyOf",
                    buildJsonArray {
                        add(schema)
                        add(buildJsonObject { put("type", "null") })
                    },
                )
            }
        } else {
            buildJsonObject {
                schema.forEach { (key, value) ->
                    if (key == "type" && value is JsonPrimitive) {
                        put(
                            "type",
                            buildJsonArray {
                                add(value)
                                add(JsonPrimitive("null"))
                            },
                        )
                    } else {
                        put(key, value)
                    }
                }
            }
        }

    private fun primitiveSchema(kind: SerialKind): JsonObject =
        buildJsonObject {
            when (kind) {
                PrimitiveKind.BOOLEAN -> put("type", "boolean")
                PrimitiveKind.BYTE, PrimitiveKind.SHORT, PrimitiveKind.INT, PrimitiveKind.LONG -> put("type", "integer")
                PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> put("type", "number")
                PrimitiveKind.CHAR -> {
                    put("type", "string")
                    put("minLength", 1)
                    put("maxLength", 1)
                }

                PrimitiveKind.STRING -> put("type", "string")
                else -> error("Unsupported primitive $kind")
            }
        }
}

// Collects the polymorphic registrations of a SerializersModule. defaultSerializer and
// defaultDeserializer are ignored deliberately: UnknownComponent/UnknownAction are a client-side
// fallback, never a server's answer, and therefore not part of the wire schema.
@OptIn(ExperimentalSerializationApi::class)
class PolymorphicDump : SerializersModuleCollector {
    data class Entry(
        val hierarchy: String,
        val wireName: String,
        val descriptor: SerialDescriptor,
    )

    val entries = mutableListOf<Entry>()

    override fun <T : Any> contextual(
        kClass: KClass<T>,
        provider: (typeArgumentsSerializers: List<KSerializer<*>>) -> KSerializer<*>,
    ) = Unit

    override fun <Base : Any, Sub : Base> polymorphic(
        baseClass: KClass<Base>,
        actualClass: KClass<Sub>,
        actualSerializer: KSerializer<Sub>,
    ) {
        val hierarchy = baseClass.simpleName ?: error("Anonymous base of a polymorphic hierarchy: $baseClass")
        entries += Entry(hierarchy, actualSerializer.descriptor.serialName, actualSerializer.descriptor)
    }

    override fun <Base : Any> polymorphicDefaultSerializer(
        baseClass: KClass<Base>,
        defaultSerializerProvider: (value: Base) -> SerializationStrategy<Base>?,
    ) = Unit

    override fun <Base : Any> polymorphicDefaultDeserializer(
        baseClass: KClass<Base>,
        defaultDeserializerProvider: (className: String?) -> DeserializationStrategy<Base>?,
    ) = Unit
}
