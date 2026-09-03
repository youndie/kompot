package io.github.youndie.kompot.studio.stories

import androidx.compose.runtime.Composable

// THE THIRD LIST, and the only one that is compositions rather than bodies: the screenshot fixtures a
// deployment already annotates. KSP writes them into one registry, and until now the only thing that
// read it was the screenshot run.
//
// Reflection for the same reason the capture is reflective — the generated registry lives in the
// CONSUMER's build, never in this one, and its dependency carries a test framework. Absent is the
// normal case, and absent means an empty list, not an error.
internal data class ViddikStory(
    val group: String,
    val name: String,
    val width: Int,
    val height: Int,
    val content: @Composable () -> Unit,
)

internal fun viddikStories(loader: ClassLoader = ViddikStory::class.java.classLoader): List<ViddikStory> =
    runCatching {
        val registry = Class.forName(REGISTRY, true, loader)
        // An `object` with a `val`, which on the JVM is an INSTANCE field and a getter; a top-level
        // `val` would be a static getter on a file class. Both are tried, because which one it is is
        // the generator's choice and not a contract.
        val holder = runCatching { registry.getField("INSTANCE").get(null) }.getOrNull()
        val components = registry.getMethod("getComponents").invoke(holder) as List<*>

        components.mapNotNull { component ->
            component ?: return@mapNotNull null
            val type = component.javaClass

            @Suppress("UNCHECKED_CAST")
            ViddikStory(
                group = type.getMethod("getGroup").invoke(component) as String,
                name = type.getMethod("getName").invoke(component) as String,
                width = type.getMethod("getWidth").invoke(component) as Int,
                height = type.getMethod("getHeight").invoke(component) as Int,
                // A @Composable () -> Unit is a Function2<Composer, Int, Unit> at runtime, which is
                // exactly what this property holds — so the cast is a rename, not a conversion.
                content = type.getMethod("getContent").invoke(component) as @Composable () -> Unit,
            )
        }
    }.getOrDefault(emptyList())

// Confirmed against the processor of the line this toolkit is on (0.1.1.8) rather than assumed from a
// newer one: the class the generator writes, and the property it puts the list in.
private const val REGISTRY = "ru.workinprogress.viddik.generated.GeneratedViddikRegistry"
