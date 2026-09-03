package io.github.youndie.kompot.registry.processor

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.ksp.writeTo

private const val KOMPOT_COMPONENT_MARKER_FQN = "io.github.youndie.kompot.registry.KompotComponentMarker"
private const val KOMPOT_COMPONENT_FQN = "io.github.youndie.kompot.KompotComponent"
private const val KOMPOT_COMPONENT_RENDERER_FQN = "io.github.youndie.kompot.KompotComponentRenderer"
private const val GENERATED_PACKAGE = "io.github.youndie.kompot.generated"

// The prose of one type, on its way from a KDoc to a schema.
private data class DocEntry(
    val summary: String?,
    val properties: Map<String, String>,
)

private data class ComponentEntry(
    val className: ClassName,
)

private data class RendererEntry(
    val componentClassName: ClassName,
    val rendererClassName: ClassName,
)

// Replaces hand-written subclass(Foo::class) / mapOf(Foo::class to FooRenderer()) lists in every
// Kompot module: it scans for @KompotComponentMarker (see :kompot-registry-annotations) and, based
// on what the marked class implements, generates ONE of two things — a module may use both, for
// instance when a data class and its renderer live in the same compilation unit:
// - KompotComponent -> an entry in Generated<Tag>SerializersModule (polymorphic registration)
// - KompotComponentRenderer<T> -> an entry in Generated<Tag>Renderers, where T is resolved from the
//   renderer's own generic argument rather than from the annotation, so a renderer and its
//   component cannot drift apart
// <Tag> comes from the KSP option kompotModuleTag, unique per consuming module (see
// KompotRegistryProcessorProvider): otherwise several modules would generate files and objects of
// the same name in one package and collide in the consumer's build.
//
// A COMPONENT AND ITS RENDERER NEED NOT SHARE A MODULE, and for anything with a server they should
// not: a renderer needs Compose and a server does not have it, so a component declared beside its
// renderer is a component the server cannot construct. The split works because the two halves are
// found differently — a component is found by its own module's run of this processor, and a renderer
// carries the component in its type argument, resolved through the compile classpath. :kompot-forms
// and :kompot-forms-client are exactly that pair and the registration they generate is the proof.
// Internal: KSP reaches this module through the provider named in META-INF/services and nowhere
// else, and the provider hands back a SymbolProcessor. Nobody constructs this class, and a
// consumer who could would be configuring the processor around KSP rather than through it.
internal class KompotRegistrySymbolProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val moduleTag: String,
) : SymbolProcessor {
    private var invoked = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (invoked) return emptyList()
        invoked = true

        val annotated = resolver.getSymbolsWithAnnotation(KOMPOT_COMPONENT_MARKER_FQN).toList()
        if (annotated.isEmpty()) return emptyList()

        val components = mutableListOf<ComponentEntry>()
        val docs = mutableListOf<Pair<String, DocEntry>>()
        val renderers = mutableListOf<RendererEntry>()
        val sourceFiles = mutableListOf<KSFile>()

        for (symbol in annotated) {
            if (symbol !is KSClassDeclaration) {
                logger.error("@KompotComponentMarker can only be applied to classes", symbol)
                continue
            }

            val superTypes = symbol.getAllSuperTypes().toList()
            val implementsComponent = superTypes.any { it.declaration.qualifiedName?.asString() == KOMPOT_COMPONENT_FQN }
            val rendererSuperType = superTypes.firstOrNull { it.declaration.qualifiedName?.asString() == KOMPOT_COMPONENT_RENDERER_FQN }

            when {
                implementsComponent && rendererSuperType != null -> {
                    logger.error(
                        "@KompotComponentMarker class must implement exactly one of KompotComponent/KompotComponentRenderer<T>, " +
                            "not both: ${symbol.qualifiedName?.asString()}",
                        symbol,
                    )
                }

                implementsComponent -> {
                    components += ComponentEntry(ClassName(symbol.packageName.asString(), symbol.simpleName.asString()))
                    documentationOf(symbol)?.let { docs += it }
                    symbol.containingFile?.let { sourceFiles += it }
                }

                rendererSuperType != null -> {
                    val componentType = rendererSuperType.arguments.firstOrNull()?.type?.resolve()
                    // isError, and it is the whole point of this branch reading the way it does. An
                    // unresolved type still answers with a declaration — carrying the REFERENCE's
                    // package rather than the real one — so without this the processor writes a
                    // plausible-looking name into generated code and the failure arrives as
                    // "Unresolved reference '<ERROR TYPE: Foo>'" in a file nobody wrote. That reads as
                    // "the processor cannot see components from other modules", which is not what
                    // happened and sends the reader to rearrange their modules.
                    val componentClassName = componentType?.takeUnless { it.isError }?.toClassNameOrNull()
                    if (componentClassName == null) {
                        logger.error(
                            "Could not resolve the component type of KompotComponentRenderer<T> on " +
                                "${symbol.qualifiedName?.asString()}. A renderer MAY be declared in a different module " +
                                "from its component, and for a server-driven toolkit that is the point: the component is " +
                                "a wire contract a headless server has to be able to build, the renderer is a platform. " +
                                "What is required is that the module declaring the component is on THIS module's compile " +
                                "classpath, and that it runs the processor itself for its own registration.",
                            symbol,
                        )
                    } else {
                        renderers +=
                            RendererEntry(
                                componentClassName = componentClassName,
                                rendererClassName = ClassName(symbol.packageName.asString(), symbol.simpleName.asString()),
                            )
                        symbol.containingFile?.let { sourceFiles += it }
                    }
                }

                else -> {
                    logger.error(
                        "@KompotComponentMarker class must implement KompotComponent or KompotComponentRenderer<T>: " +
                            "${symbol.qualifiedName?.asString()}",
                        symbol,
                    )
                }
            }
        }

        if (components.isNotEmpty() || renderers.isNotEmpty()) {
            val dependencies = Dependencies(aggregating = true, *sourceFiles.toTypedArray())
            generateRegistration(components, renderers, docs, dependencies)
        }

        return emptyList()
    }

    private fun KSType.toClassNameOrNull(): ClassName? {
        val declaration = this.declaration as? KSClassDeclaration ?: return null
        return ClassName(declaration.packageName.asString(), declaration.simpleName.asString())
    }

    // WHAT A TYPE MEANS, read from its KDoc and from nowhere else.
    //
    // `docString` is KDoc only: the `//` comments this toolkit is written in are invisible here, and
    // that is the design rather than a limitation met. Those comments explain the CODE — why a property
    // exists, what broke before it, which order a fallback happens in — and a schema that carried them
    // would be longer and harder to read for the one audience it has: somebody implementing this wire
    // on another stack. Writing a KDoc is therefore an explicit act meaning "this sentence is for the
    // schema".
    //
    // Keyed by @SerialName where there is one, because that is what the descriptor will call the type
    // and therefore the only name both sides of this carry agree on.
    private fun documentationOf(symbol: KSClassDeclaration): Pair<String, DocEntry>? {
        val summary = symbol.docString?.let(::oneParagraph)

        val properties =
            symbol.primaryConstructor
                ?.parameters
                .orEmpty()
                .mapNotNull { parameter ->
                    val name = parameter.name?.asString() ?: return@mapNotNull null
                    // The KDoc of the PROPERTY the parameter declares, which is where a data class's
                    // documentation is actually written; a parameter carries none of its own.
                    val property =
                        symbol.getDeclaredProperties().firstOrNull { it.simpleName.asString() == name }
                    val sentence = property?.docString?.let(::oneParagraph) ?: return@mapNotNull null
                    name to sentence
                }.toMap()

        if (summary == null && properties.isEmpty()) return null
        return serialNameOf(symbol) to DocEntry(summary, properties)
    }

    // The @SerialName a type carries, or its qualified name — exactly what kotlinx puts in a
    // descriptor's serialName, so the schema generator can look it up without a second convention.
    private fun serialNameOf(symbol: KSClassDeclaration): String {
        val serialName =
            symbol.annotations
                .firstOrNull { it.shortName.asString() == "SerialName" }
                ?.arguments
                ?.firstOrNull()
                ?.value as? String
        return serialName ?: symbol.qualifiedName?.asString().orEmpty()
    }

    // One paragraph, tags dropped. A KDoc's `@param`/`@return` describe a call, and a schema has
    // neither; a blank line means the author moved on to a second thought, and a description is one
    // sentence or it is not read at all.
    private fun oneParagraph(doc: String): String? =
        doc
            .lines()
            .map { it.trim().removePrefix("*").trim() }
            .takeWhile { !it.startsWith("@") }
            .joinToString("\n") { it }
            .substringBefore("\n\n")
            .replace("\n", " ")
            .trim()
            .takeIf { it.isNotEmpty() }

    private fun generateRegistration(
        components: List<ComponentEntry>,
        renderers: List<RendererEntry>,
        docs: List<Pair<String, DocEntry>>,
        dependencies: Dependencies,
    ) {
        val fileName = "Generated${moduleTag}KompotRegistration"
        val fileSpec = FileSpec.builder(GENERATED_PACKAGE, fileName)

        if (components.isNotEmpty()) {
            fileSpec.addProperty(serializersModuleProperty(components))
        }
        if (renderers.isNotEmpty()) {
            fileSpec.addProperty(renderersProperty(renderers))
        }
        if (docs.isNotEmpty()) {
            fileSpec.addProperty(docsProperty(docs))
        }

        fileSpec.build().writeTo(codeGenerator, dependencies)
    }

    private fun serializersModuleProperty(components: List<ComponentEntry>): PropertySpec {
        val serializersModuleClass = ClassName("kotlinx.serialization.modules", "SerializersModule")
        val serializersModuleFn = MemberName("kotlinx.serialization.modules", "SerializersModule")
        val polymorphicFn = MemberName("kotlinx.serialization.modules", "polymorphic")
        val subclassFn = MemberName("kotlinx.serialization.modules", "subclass")
        val kompotComponentClass = ClassName("io.github.youndie.kompot", "KompotComponent")

        val initializer = CodeBlock.builder()
        initializer.add("%M {\n", serializersModuleFn)
        initializer.indent()
        initializer.add("%M(%T::class) {\n", polymorphicFn, kompotComponentClass)
        initializer.indent()
        components.forEach { entry ->
            initializer.add("%M(%T::class)\n", subclassFn, entry.className)
        }
        initializer.unindent()
        initializer.add("}\n")
        initializer.unindent()
        initializer.add("}")

        return PropertySpec
            .builder("generated${moduleTag}SerializersModule", serializersModuleClass)
            .initializer(initializer.build())
            .build()
    }

    private fun docsProperty(docs: List<Pair<String, DocEntry>>): PropertySpec {
        val docClass = ClassName("io.github.youndie.kompot.registry", "KompotComponentDoc")
        val mapType = MAP.parameterizedBy(STRING, docClass)

        val initializer = CodeBlock.builder()
        initializer.add("mapOf(\n")
        initializer.indent()
        docs.sortedBy { it.first }.forEach { (serialName, entry) ->
            initializer.add("%S to %T(\n", serialName, docClass)
            initializer.indent()
            entry.summary?.let { initializer.add("summary = %S,\n", it) }
            if (entry.properties.isNotEmpty()) {
                initializer.add("properties = mapOf(\n")
                initializer.indent()
                entry.properties.toSortedMap().forEach { (name, sentence) ->
                    initializer.add("%S to %S,\n", name, sentence)
                }
                initializer.unindent()
                initializer.add("),\n")
            }
            initializer.unindent()
            initializer.add("),\n")
        }
        initializer.unindent()
        initializer.add(")")

        return PropertySpec
            .builder("generated${moduleTag}Docs", mapType)
            .initializer(initializer.build())
            .build()
    }

    private fun renderersProperty(renderers: List<RendererEntry>): PropertySpec {
        val kClassClass = ClassName("kotlin.reflect", "KClass")
        val kompotComponentClass = ClassName("io.github.youndie.kompot", "KompotComponent")
        val kompotComponentRendererClass = ClassName("io.github.youndie.kompot", "KompotComponentRenderer")
        val outComponent = WildcardTypeName.producerOf(kompotComponentClass)
        val mapType =
            MAP.parameterizedBy(
                kClassClass.parameterizedBy(outComponent),
                kompotComponentRendererClass.parameterizedBy(outComponent),
            )

        val initializer = CodeBlock.builder()
        initializer.add("mapOf(\n")
        initializer.indent()
        renderers.forEach { entry ->
            initializer.add("%T::class to %T(),\n", entry.componentClassName, entry.rendererClassName)
        }
        initializer.unindent()
        initializer.add(")")

        return PropertySpec
            .builder("generated${moduleTag}Renderers", mapType)
            .initializer(initializer.build())
            .build()
    }
}
