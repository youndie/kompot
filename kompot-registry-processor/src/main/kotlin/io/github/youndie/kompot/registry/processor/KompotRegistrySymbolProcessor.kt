package io.github.youndie.kompot.registry.processor

import com.google.devtools.ksp.getAllSuperTypes
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
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.ksp.writeTo

private const val KOMPOT_COMPONENT_MARKER_FQN = "io.github.youndie.kompot.registry.KompotComponentMarker"
private const val KOMPOT_COMPONENT_FQN = "io.github.youndie.kompot.KompotComponent"
private const val KOMPOT_COMPONENT_RENDERER_FQN = "io.github.youndie.kompot.KompotComponentRenderer"
private const val GENERATED_PACKAGE = "io.github.youndie.kompot.generated"

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
            generateRegistration(components, renderers, dependencies)
        }

        return emptyList()
    }

    private fun KSType.toClassNameOrNull(): ClassName? {
        val declaration = this.declaration as? KSClassDeclaration ?: return null
        return ClassName(declaration.packageName.asString(), declaration.simpleName.asString())
    }

    private fun generateRegistration(
        components: List<ComponentEntry>,
        renderers: List<RendererEntry>,
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
