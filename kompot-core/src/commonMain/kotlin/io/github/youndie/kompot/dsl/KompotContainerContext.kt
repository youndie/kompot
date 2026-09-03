package io.github.youndie.kompot.dsl

import io.github.youndie.kompot.KompotComponent
import kotlin.uuid.Uuid

@KompotDsl
public interface KompotContainerContext {
    public fun addComponent(component: KompotComponent)

    // THE ID A NODE GETS WHEN NOBODY NAMED IT, and it is a PATH rather than a fresh identifier.
    //
    // An id has one requirement — unique inside the tree — and a random one meets it while quietly
    // failing three things that matter. Two calls of the same DSL produced two different trees, so a
    // diff by id read a one-word edit as every node being replaced. A recorded fixture regenerated at
    // will differed from itself. And a live update addresses a node BY id (LocalKompotRealtimeUpdates),
    // so a node whose id is new on every render could not be addressed at all.
    //
    // Called once per child, BEFORE the child is added, so the count it reads is that child's index.
    //
    // The default keeps the old behaviour for a container somebody else wrote: an implementation that
    // does not know its own place in a tree cannot produce a path, and guessing one would collide.
    public fun nextChildPath(): String = Uuid.random().toString()
}
