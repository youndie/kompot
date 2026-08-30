package io.github.youndie.kompot.analytics

import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import kotlin.reflect.KClass

// This module depends on no component plug-in, so it cannot know the event names of concrete
// components and actions. It defines the contract only; the entries are registered by the application
// at its composition root, exactly where it already merges renderer maps and serializers modules.
public fun interface KompotComponentEventNaming {
    public fun describe(component: KompotComponent): KompotEventDescriptor
}

public fun interface KompotActionEventNaming {
    public fun describe(action: KompotAction): KompotEventDescriptor
}

public class KompotEventNamingRegistry(
    private val componentNaming: Map<KClass<out KompotComponent>, KompotComponentEventNaming> = emptyMap(),
    private val actionNaming: Map<KClass<out KompotAction>, KompotActionEventNaming> = emptyMap(),
) {
    // An unregistered type does not break tracking: it degrades to the class name, the same quiet but
    // visible fallback as an unknown component placeholder rather than an exception.
    public fun describe(component: KompotComponent): KompotEventDescriptor =
        componentNaming[component::class]?.describe(component)
            ?: KompotEventDescriptor(
                eventName = component::class.simpleName ?: "unknown_component",
                properties = mapOf("componentId" to component.id),
            )

    public fun describe(action: KompotAction): KompotEventDescriptor =
        actionNaming[action::class]?.describe(action)
            ?: KompotEventDescriptor(eventName = action::class.simpleName ?: "unknown_action")
}
