package io.github.youndie.kompot.studio.tree

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.lazy.tree.TreeGeneratorScope
import org.jetbrains.jewel.foundation.lazy.tree.buildTree
import org.jetbrains.jewel.ui.component.LazyTree
import org.jetbrains.jewel.ui.component.Text

// The tree panel. Jewel's LazyTree is used through this one file on purpose: it is pre-1.0 and its
// API is marked experimental, so the whole cost of a version that moves is kept where it can be seen
// — the opt-in below is the marker of exactly that, and it is on one function rather than on the
// module, which would silence it everywhere for the sake of this one call.
@OptIn(ExperimentalJewelApi::class)
@Composable
internal fun ScreenTreePane(
    root: ScreenNode?,
    modifier: Modifier = Modifier,
    onSelect: (ScreenNode) -> Unit,
) {
    if (root == null) {
        Text("The body carries no component tree.", modifier.padding(8.dp))
        return
    }

    // Keyed on the root: a body that changed is a different tree, and rebuilding it is what puts the
    // new nodes on screen. The path is the element id, so expansion survives an edit that did not move
    // the node.
    val tree = remember(root) { buildTree<ScreenNode> { addScreenNode(root) } }

    LazyTree(
        tree = tree,
        modifier = modifier,
        onElementClick = { element -> onSelect(element.data) },
    ) { element ->
        val node = element.data
        Row(Modifier.padding(vertical = 2.dp)) {
            // A marker rather than a colour: a type the profile does not carry is the single most
            // useful thing this panel can say, and it has to survive a screenshot and a colour-blind
            // reader.
            Text(if (node.known) node.label else "⚠ ${node.label}")
        }
    }
}

// One function for both scopes, because TreeGeneratorScope is what the builder and the children
// generator share — the alternative is the same four lines written twice, which is where a tree that
// nests correctly at the top level and flattens two levels down comes from.
private fun TreeGeneratorScope<ScreenNode>.addScreenNode(node: ScreenNode) {
    if (node.children.isEmpty()) {
        addLeaf(node, id = node.path)
    } else {
        addNode(node, id = node.path) { node.children.forEach { addScreenNode(it) } }
    }
}
