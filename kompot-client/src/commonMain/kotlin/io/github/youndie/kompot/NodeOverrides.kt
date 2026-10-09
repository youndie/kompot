package io.github.youndie.kompot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * What one screen's nodes are now, by `id`, over what its tree said (SPEC.md §4.4).
 *
 * One store per screen, and every writer goes through it: live frames today
 * ([KompotRealtimeProvider]), any other answer that replaces a node by `id` later. `RenderNode` is the
 * only reader — it substitutes the override for the node before dispatch, so no renderer knows the
 * store exists.
 *
 * An override lives by three rules, and they are what makes it safe to keep the tree on screen while
 * the next one loads:
 * - **a whole tree that arrives drops every override** written before it: the tree is the truth.
 *   "Arrives" is a delivery, not a difference: a load of [KompotScreenLoader] that completed, or a
 *   new `arrival` the application hands [KompotScreen] / [KompotLazyScreen] with the tree — and a tree
 *   equal to the one on screen arrives as much as any other (after an `update`, "back" loads exactly
 *   the tree the overrides were written over). Equality matters only where nothing was delivered: a
 *   recomposition that hands the screen an equal tree again keeps the overrides. State under ids — a
 *   chosen tab, an opened section, a list's loaded pages — is not an override and stays;
 * - **an override of `X` drops the overrides of the nodes inside the previous `X`**: the new `X`
 *   came whole, with its own children;
 * - **an `id` the tree does not have is ignored** (§10.2): it never applies to a node that turns up
 *   later, because a node can only turn up through a newer tree or a newer override of an ancestor.
 *
 * The rules are held by order rather than by walking the tree: every write and every tree takes the
 * next number, and an override applies under a node only when it is newer than whatever drew that
 * node — the tree, or an override of an ancestor. Walking would need the children of every component
 * type, including a plugin's, which the client cannot name; order needs nothing.
 *
 * Written from the main thread — the composition's own — as frames and actions are.
 */
@Stable
public class KompotNodeOverrides {
    internal class Entry(
        val component: KompotComponent,
        val order: Long,
    )

    private val entries = mutableStateMapOf<String, Entry>()

    // Not state: nothing draws the counter, it only hands out the next number.
    private var written = 0L

    /**
     * Says the node [id] is now [component], until the screen gets a newer tree or a newer override
     * of a node around it. A later override of the same [id] replaces this one.
     */
    public fun override(
        id: String,
        component: KompotComponent,
    ) {
        written += 1
        entries[id] = Entry(component, written)
    }

    // A whole tree arrived: the number it takes is the floor below which nothing written applies.
    internal fun nextTree(): Long {
        written += 1
        return written
    }

    internal fun lookup(
        id: String,
        floor: Long,
    ): Entry? = entries[id]?.takeIf { it.order > floor }

    // Housekeeping only — lookup already ignores everything below the floor.
    internal fun forgetBefore(floor: Long) {
        entries.filterValues { it.order < floor }.keys.forEach(entries::remove)
    }
}

/**
 * The override store of the screen being drawn. `null` outside a screen; [KompotScreen],
 * [KompotLazyScreen] and [KompotRealtimeProvider] each provide one when they find none above them, so
 * an application provides its own only to write into it — `remember { KompotNodeOverrides() }` above
 * the screen.
 */
public val LocalKompotNodeOverrides: ProvidableCompositionLocal<KompotNodeOverrides?> =
    staticCompositionLocalOf { null }

// The order of whatever drew the nearest node above: the tree, or an override of an ancestor. Not
// static: an override arriving changes it under one node, and only that subtree should recompose.
internal val LocalKompotOverrideFloor: ProvidableCompositionLocal<Long> = compositionLocalOf { 0L }

// The override that applies to [id] here, if any.
@Composable
internal fun currentOverride(id: String): KompotNodeOverrides.Entry? =
    LocalKompotNodeOverrides.current?.lookup(id, LocalKompotOverrideFloor.current)

// Which delivery the tree a KompotScreenLoader draws came with: the number of the load that brought it.
// Read by the nearest screen below, which takes it as that tree's arrival; null where no loader is.
internal val LocalKompotTreeArrival: ProvidableCompositionLocal<Any?> = compositionLocalOf { null }

// A screen's tree enters here: the store is found or made, and a tree that arrives takes the next
// number, which drops every override written before it.
@Composable
internal fun ProvideScreenOverrides(
    root: KompotComponent,
    arrival: Any?,
    content: @Composable () -> Unit,
) {
    val overrides = LocalKompotNodeOverrides.current ?: remember { KompotNodeOverrides() }
    val delivered = LocalKompotTreeArrival.current
    // A delivery is an arrival whatever the tree is: the application's own `arrival`, or the loader's
    // number of the load. Where neither changed, the tree's value decides, so a recomposition that hands
    // over an equal tree keeps the overrides (see KompotNodeOverrides). Taking the number here, in
    // composition, rather than in an effect is what keeps the first frame of a new tree from being
    // drawn under the overrides it replaces; the counter is not state, so nothing is written back.
    val floor = remember(overrides, root, arrival, delivered) { overrides.nextTree() }
    LaunchedEffect(overrides, floor) { overrides.forgetBefore(floor) }
    CompositionLocalProvider(
        LocalKompotNodeOverrides provides overrides,
        LocalKompotOverrideFloor provides floor,
    ) {
        content()
    }
}
