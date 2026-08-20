package io.github.youndie.kompot.dsl

import io.github.youndie.kompot.KompotComponent

@KompotDsl
interface KompotContainerContext {
    fun addComponent(component: KompotComponent)
}
