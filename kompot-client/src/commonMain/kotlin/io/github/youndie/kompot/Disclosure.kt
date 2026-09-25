package io.github.youndie.kompot

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.standard.ExpandableComponent
import io.github.youndie.kompot.standard.TabsComponent

// The two nodes that change the screen without asking the server (SPEC.md §4.12).
//
// The state is the reader's, and the value on the wire is where it STARTS. It is kept under the node's
// id, and applied again only when a later version of the node carries a DIFFERENT value than the one
// last seen: a navigate to the same screen, a refresh, a live frame that repeats the value all leave
// the reader's tab where they put it, while a server that really changed its mind still wins — which
// is §4.4 ("a fresh version is applied") taken literally rather than as "reset on every arrival".
@Composable
private fun <T> rememberSeeded(
    id: String,
    wire: T,
): MutableState<T> {
    val state = remember(id) { mutableStateOf(wire) }
    // Keyed on the wire value alone: an unchanged value does not relaunch, so it cannot undo a choice.
    // One frame late, like the fresh-version rule of paginated_list, and for the same reason: the value
    // is applied as an event, not recomputed on every composition.
    LaunchedEffect(id, wire) { state.value = wire }
    return state
}

public class TabsRenderer : KompotComponentRenderer<TabsComponent> {
    @Composable
    override fun Render(
        component: TabsComponent,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        if (component.tabs.isEmpty()) return
        val registry = LocalKompotRegistry.current
        var selected by rememberSeeded(component.id, component.selected)
        // Outside the list means the first, whether the server sent it or the list got shorter since.
        val current = selected.takeIf { it in component.tabs.indices } ?: 0

        Column(component.modifiers.toComposeModifier()) {
            // Material's tab row carries Role.Tab and the selected state for a screen reader, so the
            // strip is announced as tabs with one of them chosen — nothing to add here.
            PrimaryTabRow(selectedTabIndex = current) {
                component.tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = index == current,
                        onClick = { selected = index },
                        text = { Text(tab.title) },
                    )
                }
            }
            // Only the selected pane is composed: a pane that is not shown has nothing to report, and
            // its impressions (B-54) are not counted until someone opens it.
            registry.RenderNode(component.tabs[current].content, actionHandler, formController)
        }
    }
}

public class ExpandableRenderer : KompotComponentRenderer<ExpandableComponent> {
    @Composable
    override fun Render(
        component: ExpandableComponent,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        val registry = LocalKompotRegistry.current
        var expanded by rememberSeeded(component.id, component.expanded)

        Column(component.modifiers.toComposeModifier()) {
            // The header is a button that expands or collapses, and says which through the expand and
            // collapse actions — the platform's own words for a disclosure, in the reader's language.
            // A stateDescription written here would be English on every phone. Its own children are
            // read as its label, as with any actionable container (§4.11).
            Column(
                Modifier
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .semantics {
                        if (expanded) {
                            collapse {
                                expanded = false
                                true
                            }
                        } else {
                            expand {
                                expanded = true
                                true
                            }
                        }
                    },
            ) {
                registry.RenderNode(component.header, actionHandler, formController)
            }
            if (expanded) registry.RenderNode(component.content, actionHandler, formController)
        }
    }
}
