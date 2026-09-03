package io.github.youndie.kompot.studio.tree

import io.github.youndie.kompot.spec.Slot

// WHERE A DROP LANDS: a parent, one of its declared slots, and a position in it.
//
// `replacing` is the one case a person has to be asked about. A slot holding a single component —
// `paginated_list.emptyState`, `wizard_screen.content` — has room for exactly one, so a drop there
// is not an addition but an overwrite, and an editor that performs it silently is one that eats work.
internal data class DropTarget(
    val parentPath: String,
    val slot: String,
    val index: Int,
    val replacing: Boolean,
)

// A drop ON a node means two different things and both are what somebody expects:
//
//   - onto a container — put it inside, at the end;
//   - onto anything else — put it next to it, after.
//
// Reading the second from the target's own path rather than from a walk of the tree is deliberate.
// The path already says which parent and which slot the node sits in, because that is what the path
// IS; a lookup would be a second answer to a question already answered, and second answers drift.
internal fun dropTargetFor(
    target: ScreenNode,
    slots: Map<String, List<Slot>>,
): DropTarget? {
    slots[target.wireType].orEmpty().firstOrNull { it.many }?.let { slot ->
        val filled = target.children.count { it.path.startsWith("${target.path}.${slot.name}[") }
        return DropTarget(target.path, slot.name, filled, replacing = false)
    }

    val position = target.path.substringAfterLast('.', "")
    val slot = position.substringBefore('[')
    val index = position.substringAfter('[', "").removeSuffix("]").toIntOrNull()

    return when {
        // A sibling in a list: after the node the drop landed on.
        index != null -> DropTarget(target.path.substringBeforeLast('.'), slot, index + 1, replacing = false)

        // The only child of a single-value slot, or the root itself: there is no "next to" here, and
        // the honest answers are "replace this" and "nowhere".
        slot.isNotEmpty() && !slot.startsWith("$") ->
            DropTarget(target.path.substringBeforeLast('.'), slot, 0, replacing = true)

        else -> null
    }
}

// A node cannot be dropped into itself or into anything it contains. Paths make that a prefix test,
// which is the reason the tree is keyed by path and not by an identity that says nothing about where
// a node sits.
internal fun canMove(
    from: String,
    into: DropTarget,
): Boolean = into.parentPath != from && !into.parentPath.startsWith("$from.") && !into.parentPath.startsWith("$from[")
