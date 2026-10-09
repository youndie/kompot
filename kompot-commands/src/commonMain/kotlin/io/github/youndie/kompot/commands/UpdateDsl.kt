package io.github.youndie.kompot.commands

import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.dsl.KompotContainerContext
import io.github.youndie.kompot.dsl.KompotDsl
import io.github.youndie.kompot.realtime.UpdateComponentMessage

/**
 * Collects the nodes of an `update` with the same calls a screen is written with: every node added
 * here becomes a frame addressed by its own `id`, so a frame cannot name one node and carry another.
 */
@KompotDsl
public class UpdateBuilder : KompotContainerContext {
    private val updates = mutableListOf<UpdateComponentMessage>()

    override fun addComponent(component: KompotComponent) {
        updates += UpdateComponentMessage(componentId = component.id, component = component)
    }

    // A node of an update replaces the node the screen has under the same id. One left unnamed would
    // get a path the screen never had, and a client ignores a frame for an id it does not have
    // (SPEC.md §10.2): the update would arrive and change nothing, silently.
    override fun nextChildPath(): String =
        error(
            "a node of an update replaces the node with the same id on the screen (SPEC.md §10.2), so it needs id = …",
        )

    public fun build(
        deeplink: String?,
        history: String?,
    ): UpdateAction = UpdateAction(updates = updates.toList(), deeplink = deeplink, history = history)
}

/**
 * An `update` (SPEC.md §16.4): the nodes [block] adds replace the nodes with the same ids, in order.
 * [deeplink] is the address the screen has afterwards, and [history] — one of [UpdateHistory] — how it
 * enters the application's history.
 *
 * ```
 * kompotUpdate(deeplink = "app://catalog?brand=acme") {
 *     column(id = "results") { … }
 *     text("3 in the cart", id = "cart-badge")
 * }
 * ```
 */
public fun kompotUpdate(
    deeplink: String? = null,
    history: String? = null,
    block: UpdateBuilder.() -> Unit,
): UpdateAction = UpdateBuilder().apply(block).build(deeplink, history)
