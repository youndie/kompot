package io.github.youndie.kompot.dsl

import io.github.youndie.kompot.KompotComponent

@KompotDsl
public interface KompotContainerContext {
    public fun addComponent(component: KompotComponent)
}
