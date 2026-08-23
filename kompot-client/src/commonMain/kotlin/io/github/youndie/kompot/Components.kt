@file:OptIn(kotlinx.coroutines.FlowPreview::class)

package io.github.youndie.kompot

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import io.github.youndie.kompot.standard.*
import io.github.youndie.kompot.form.FieldValue
import io.github.youndie.kompot.form.FormController
import kotlin.reflect.KClass
import androidx.compose.foundation.layout.Column as ComposeColumn

// The registry is the only dispatch mechanism: there is no hard-wired `when` over types anywhere, so
// EVERY component type — the standard column/row/text/button and UnknownComponent included — has to be
// registered in a KompotRegistry explicitly. An application merges the renderer maps below into one.
val LocalKompotRegistry =
    staticCompositionLocalOf<KompotRegistry> {
        error("LocalKompotRegistry not provided")
    }

// The contract for fetching a page of a list. An application plugs in a concrete HTTP implementation
// the same way it plugs in the registry, the design system and the action handler: one
// CompositionLocalProvider at the root.
val LocalKompotPageLoader =
    staticCompositionLocalOf<KompotPageLoader> {
        error("LocalKompotPageLoader not provided")
    }

class ColumnRenderer : KompotComponentRenderer<ColumnComponent> {
    @Composable
    override fun Render(
        component: ColumnComponent,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        val registry = LocalKompotRegistry.current
        ComposeColumn(
                // The tap goes on the CONTAINER rather than on a child: the whole row is the target,
                // which is the gesture a list of openable items expects.
            modifier = component.modifiers.toComposeModifier().clickableWith(component.action, actionHandler),
            verticalArrangement = Arrangement.spacedBy(component.spacing.dp),
        ) {
            component.children.forEach { child ->
                    // weight is a share of the height inside a column, carried by a modifier node on
                    // the CHILD. Outside a column the general mapper ignores it — it is extracted and
                    // applied here, by the parent, because only the parent has a ColumnScope.
                val weight =
                    child.modifiers
                        .filterIsInstance<KompotModifierNode.Weight>()
                        .firstOrNull()
                        ?.value
                    // propagateMinConstraints is what makes weight mean what the word says. Compose's
                    // own RowScope.weight fills by default, but it fills the node it is applied to —
                    // here that is this wrapper Box, and a Box hands its child a MAXIMUM without a
                    // minimum, so the child painted its own content width while its share sat empty
                    // around it. Reserving the space worked all along; only the painting did not.
                    //
                    // Invisible while the data is long: text that wraps stretches itself to the
                    // constraint, so a screen of long titles looks right and the same tree with short
                    // ones does not.
                Box(
                    modifier = if (weight != null) Modifier.weight(weight) else Modifier,
                    propagateMinConstraints = weight != null,
                ) {
                    registry.RenderNode(child, actionHandler, formController)
                }
            }
        }
    }
}

class RowRenderer : KompotComponentRenderer<RowComponent> {
    @Composable
    override fun Render(
        component: RowComponent,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        val registry = LocalKompotRegistry.current
        Row(
            modifier = component.modifiers.toComposeModifier().clickableWith(component.action, actionHandler),
            horizontalArrangement = Arrangement.spacedBy(component.spacing.dp),
        ) {
            component.children.forEach { child ->
                    // weight is a share of the width inside a row, carried by a modifier node on the
                    // CHILD. Outside a row the general mapper ignores it — it is extracted and applied
                    // here, by the parent, because only the parent has a RowScope.
                val weight =
                    child.modifiers
                        .filterIsInstance<KompotModifierNode.Weight>()
                        .firstOrNull()
                        ?.value
                    // propagateMinConstraints is what makes weight mean what the word says. Compose's
                    // own RowScope.weight fills by default, but it fills the node it is applied to —
                    // here that is this wrapper Box, and a Box hands its child a MAXIMUM without a
                    // minimum, so the child painted its own content width while its share sat empty
                    // around it. Reserving the space worked all along; only the painting did not.
                    //
                    // Invisible while the data is long: text that wraps stretches itself to the
                    // constraint, so a screen of long titles looks right and the same tree with short
                    // ones does not.
                Box(
                    modifier = if (weight != null) Modifier.weight(weight) else Modifier,
                    propagateMinConstraints = weight != null,
                ) {
                    registry.RenderNode(child, actionHandler, formController)
                }
            }
        }
    }
}

// The read-only field renderer lives in :kompot-forms-client, in the same package.

class TextRenderer : KompotComponentRenderer<TextComponent> {
    @Composable
    override fun Render(
        component: TextComponent,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        val designSystem = LocalKompotDesignSystem.current
            // A null style means the server did not name one: the standard text component
            // deliberately has no "default" typography token, so the local default lives here, in
            // a concrete Material3 implementation.
        val style = component.style?.let { designSystem.resolveTypography(it) } ?: MaterialTheme.typography.bodyMedium
        Text(
            text = component.text,
            style = style,
                // An explicit `color` argument OVERRIDES the one inside the style, so passing anything
                // here throws away what the design system said — a token resolving to a red TextStyle
                // rendered in the ordinary colour, with nothing unknown and so nothing logged. Since
                // KompotComponentText carries no colour of its own and a ColorToken only paints a
                // background, a typography token is the only place a text colour can come from
                // (SPEC.md §6); Unspecified is what lets it through.
            color = if (style.color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else Color.Unspecified,
                // What becomes of a string that does not fit is the server's to decide, because §14
                // makes it the only party allowed to produce one. Absent, nothing is capped and the
                // text takes the lines it needs, exactly as before.
            maxLines = component.maxLines ?: Int.MAX_VALUE,
            overflow = if (component.ellipsis) TextOverflow.Ellipsis else TextOverflow.Clip,
            modifier = component.modifiers.toComposeModifier(),
        )
    }
}

class ButtonRenderer : KompotComponentRenderer<ButtonComponent> {
    @Composable
    override fun Render(
        component: ButtonComponent,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        val surface = LocalKompotDesignSystem.current.resolveSurface(KompotSurfaceRoles.button(component.variant))
        Button(
            onClick = { actionHandler.handle(component.action) },
                // ButtonDefaults.shape is CircleShape and does NOT come from MaterialTheme.shapes, so
                // a theme with square corners changed nothing here until the design system could
                // answer for the role.
            shape = surface.shape ?: ButtonDefaults.shape,
            colors =
                ButtonDefaults.buttonColors().let { base ->
                    if (surface.container == Color.Unspecified && surface.content == Color.Unspecified) {
                        base
                    } else {
                        base.copy(
                            containerColor = if (surface.container == Color.Unspecified) base.containerColor else surface.container,
                            contentColor = if (surface.content == Color.Unspecified) base.contentColor else surface.content,
                        )
                    }
                },
            border = if (surface.outline == Color.Unspecified) null else BorderStroke(1.dp, surface.outline),
            modifier = component.modifiers.toComposeModifier(),
        ) {
                // The label goes through the design system too. Without it a button's words are set in
                // the platform's fallback font: Material's own typography names no family, and the
                // label's width follows the font, so two machines disagree even about where the
                // button's edge is.
            Text(
                text = component.text,
                    // Explicitly, and not through the style: a Text resolves its colour as argument,
                    // then style.color, then LocalContentColor — so an ambient text style that names a
                    // colour of its own wins over the contentColor the Button was given, and both a
                    // primary and a quiet button come out in the theme's grey. The container half
                    // worked all along, which is what made the fill look like the only channel
                    // emphasis had.
                color = surface.content,
                style = surface.textStyle ?: LocalTextStyle.current,
            )
        }
    }
}

class TableRenderer : KompotComponentRenderer<TableComponent> {
    @Composable
    override fun Render(
        component: TableComponent,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        ComposeColumn(
            modifier =
                component.modifiers
                    .toComposeModifier()
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            component.rows.forEachIndexed { index, row ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .let { mod -> if (row.header) mod.background(MaterialTheme.colorScheme.surfaceVariant) else mod },
                ) {
                    row.cells.forEach { cell ->
                        Text(
                            text = cell,
                            style = if (row.header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f).padding(8.dp),
                        )
                    }
                }
                if (index < component.rows.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

private const val LIST_FILTER_DEBOUNCE_MS = 400L

// The flat form of a value comes from the type itself (FieldValue.plainValue). There used to be a
// `when` over concrete value types here — the engine enumerating an application's field plug-in. The
// HTTP encoding proper stays with the client.

// The pagination state, lifted out of the renderer into a class of its own so that TWO consumers can
// share it: the ordinary renderer, for lists nested inside a screen, and the lazy projection, for a
// list at the root. The fields are MutableState rather than vars in a data class — otherwise every
// update would create a new object and break the identity of the state between recompositions of the
// lazy builder.
private class PaginatedListState(
    val items: MutableState<List<KompotComponent>>,
    val nextLoadAction: MutableState<LoadPageAction?>,
    val isReloading: MutableState<Boolean>,
    val isLoadingMore: MutableState<Boolean>,
)

// Keeps the list state — current items plus the cursor of the next page — in a remember keyed on the
// component id, so changing screens resets it. With a reloadUrl set it also watches the CHANGES of the
// same form controller the neighbouring filter fields use and re-requests the first page, debounced:
// a filter, a search box and a sort order are simply fields of the same schema, and no separate action
// for changing them is needed.
@Composable
private fun rememberPaginatedListState(
    component: PaginatedListComponent,
    pageLoader: KompotPageLoader,
    formController: FormController,
): PaginatedListState {
    val items = remember(component.id) { mutableStateOf(component.initialItems) }
    val nextLoadAction = remember(component.id) { mutableStateOf(component.loadMoreAction) }
    val isReloading = remember(component.id) { mutableStateOf(false) }
    val isLoadingMore = remember(component.id) { mutableStateOf(false) }

        // A fresh version of this very component arrives under the same id from TWO directions, and
        // the remember above sees neither: the id has not changed, so the remember key has not
        // changed either. Both are therefore applied explicitly, unlike a genuine remount where the
        // id really is fresh every time.
        //
        // The first is a whole new tree for the screen — which is the ordinary way anything changes,
        // since §16.4's idiom is that an action answers with a navigate and the client re-opens the
        // screen. Without this the answer arrived, the screen reloaded and the list went on showing
        // what it was first given; on a board, where every card lives in a list, nothing a person did
        // was ever visible.
        //
        // Keyed on the component rather than on its items: equal trees must not disturb a list that
        // has since loaded further pages, and a data class compares by value.
    LaunchedEffect(component) {
        items.value = component.initialItems
        nextLoadAction.value = component.loadMoreAction
    }

        // The second is a live update for this node alone. It stays LAST so that a targeted patch is
        // not undone by a tree that merely arrived again unchanged.
    val realtimeUpdate = LocalKompotRealtimeUpdates.current[component.id] as? PaginatedListComponent
    LaunchedEffect(realtimeUpdate) {
        if (realtimeUpdate != null) {
            items.value = realtimeUpdate.initialItems
            nextLoadAction.value = realtimeUpdate.loadMoreAction
        }
    }

    val reloadUrl = component.reloadUrl
    if (reloadUrl != null) {
        LaunchedEffect(component.id) {
            formController.fieldsState
                    // The very first value is skipped: initialItems already show the same thing for
                    // the current (empty) filters, and asking again for it would be pointless.
                .drop(1)
                .debounce(LIST_FILTER_DEBOUNCE_MS)
                .collectLatest {
                    isReloading.value = true
                    try {
                        val params = formController.getRawValues().mapValues { (_, value) -> value.plainValue }
                        val response = pageLoader.loadPage(reloadUrl, params)
                        items.value = response.items
                        nextLoadAction.value = response.nextLoadAction
                    } finally {
                        isReloading.value = false
                    }
                }
        }
    }

    return PaginatedListState(items, nextLoadAction, isReloading, isLoadingMore)
}

@Composable
private fun LoadMoreButtonOrSpinner(
    next: LoadPageAction,
    state: PaginatedListState,
    pageLoader: KompotPageLoader,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    if (state.isLoadingMore.value) {
        Box(modifier = modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    } else {
        TextButton(
            onClick = {
                coroutineScope.launch {
                    state.isLoadingMore.value = true
                    try {
                        val response = pageLoader.loadPage(next.url)
                        state.items.value = state.items.value + response.items
                        state.nextLoadAction.value = response.nextLoadAction
                    } finally {
                        state.isLoadingMore.value = false
                    }
                }
            },
            modifier = modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text("Show more")
        }
    }
}

// The non-lazy projection — a Column and a forEach — for a paginated list met somewhere OTHER than
// the root of a screen, nested inside a column or a row, where virtualisation is impossible anyway.
// A whole screen uses the lazy one instead, which lifts the list's items straight into the LazyColumn.
class PaginatedListRenderer : KompotComponentRenderer<PaginatedListComponent> {
    @Composable
    override fun Render(
        component: PaginatedListComponent,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        val registry = LocalKompotRegistry.current
        val pageLoader = LocalKompotPageLoader.current
        val state = rememberPaginatedListState(component, pageLoader, formController)

            // Whether this list can scroll is decided by the box it was given, and the box is the only
            // thing that can decide it. Paging is the point of the component, and laying every item out
            // in an ordinary column meant it could never reach its own end: anything past the bottom was
            // clipped, so loadMoreAction could not fire by scrolling to it. It looked like "some
            // screens" because a root COLUMN takes the lazy projection below and scrolls; a board
            // rooted in a row took this path and scrolled nowhere.
            //
            // Unbounded height is the case that must NOT become a lazy list: inside an already
            // scrolling parent Compose cannot measure one, which is exactly why the projection exists.
        BoxWithConstraints(modifier = component.modifiers.toComposeModifier().fillMaxWidth()) {
            if (constraints.hasBoundedHeight) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    paginatedListItems(component, state, registry, actionHandler, formController, pageLoader)
                }
                return@BoxWithConstraints
            }

            ComposeColumn(modifier = Modifier.fillMaxWidth()) {
                when {
                    state.isReloading.value -> {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    state.items.value.isEmpty() -> {
                        component.emptyState?.let { registry.RenderNode(it, actionHandler, formController) }
                    }

                    else -> {
                        state.items.value.forEach { item -> registry.RenderNode(item, actionHandler, formController) }

                        val next = state.nextLoadAction.value
                        if (next != null) {
                            LoadMoreButtonOrSpinner(next, state, pageLoader)
                        }
                    }
                }
            }
        }
    }
}

// The lazy projection of the same component: the items of the list become items of the PARENT
// LazyColumn directly, rather than a nested lazy list inside an already scrolling screen — Compose
// cannot measure the infinite height of one. Used only when a paginated list is among the root's
// direct children.
//
// NOT @Composable: the content builder of a LazyColumn is not itself a composable context, so
// remember and LaunchedEffect cannot be called here. The state is passed in ready-made, computed
// before entering the LazyColumn. The item builders below are composable contexts again.
private fun LazyListScope.paginatedListItems(
    component: PaginatedListComponent,
    state: PaginatedListState,
    registry: KompotRegistry,
    actionHandler: KompotActionHandler,
    formController: FormController,
    pageLoader: KompotPageLoader,
) {
    when {
        state.isReloading.value -> {
            item(key = "${component.id}_loading") {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        state.items.value.isEmpty() -> {
            component.emptyState?.let { empty ->
                item(key = "${component.id}_empty") {
                    registry.RenderNode(empty, actionHandler, formController)
                }
            }
        }

        else -> {
            items(state.items.value, key = { it.id }) { item ->
                registry.RenderNode(item, actionHandler, formController)
            }

            val next = state.nextLoadAction.value
            if (next != null) {
                item(key = "${component.id}_load_more") {
                    LoadMoreButtonOrSpinner(next, state, pageLoader)
                }
            }
        }
    }
}

// Graceful degradation: a type this version of the client does not know — sent by a newer backend,
// say — decodes into UnknownComponent rather than taking the screen down.
//
// If the server named an equivalent, it is drawn instead of nothing. Only the server can know one: it
// chose to replace a component, so it knows what the replacement stands in for. The fallback is an
// ordinary component and may itself be unfamiliar, in which case this happens again one level down —
// which is the right behaviour, not an accident of recursion.
class UnknownComponentRenderer : KompotComponentRenderer<UnknownComponent> {
    @Composable
    override fun Render(
        component: UnknownComponent,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        val fallback = component.fallback
        if (fallback == null) {
            println("[Kompot] Unknown component \"${component.originalType}\" skipped")
            return
        }

        println("[Kompot] Unknown component \"${component.originalType}\" drawn through its fallback")
        LocalKompotRegistry.current.RenderNode(fallback, actionHandler, formController)
    }
}

// The "plug-ins" of the renderer registry: an application merges them when assembling a registry,
// exactly as it merges serializers modules.
val kompotCoreRenderers: Map<KClass<out KompotComponent>, KompotComponentRenderer<out KompotComponent>> =
    mapOf(UnknownComponent::class to UnknownComponentRenderer())

val kompotStandardRenderers: Map<KClass<out KompotComponent>, KompotComponentRenderer<out KompotComponent>> =
    mapOf(
        ColumnComponent::class to ColumnRenderer(),
        RowComponent::class to RowRenderer(),
        TextComponent::class to TextRenderer(),
        ButtonComponent::class to ButtonRenderer(),
        TableComponent::class to TableRenderer(),
        PaginatedListComponent::class to PaginatedListRenderer(),
    )

// The renderers of :kompot-forms live in :kompot-forms-client, in this same package, and their map is
// generated as generatedFormsClientRenderers.

@Composable
fun KompotNode(
    registry: KompotRegistry,
    formController: FormController,
    component: KompotComponent,
    actionHandler: KompotActionHandler,
) {
    registry.RenderNode(component, actionHandler, formController)
}

@Composable
fun KompotScreen(
    rootComponent: KompotComponent,
    registry: KompotRegistry,
    formController: FormController,
    actionHandler: KompotActionHandler,
) {
    CompositionLocalProvider(LocalKompotRegistry provides registry) {
        KompotNode(
            component = rootComponent,
            registry = registry,
            formController = formController,
            actionHandler = actionHandler,
        )
    }
}

// A whole screen as a LazyColumn instead of a Column in a vertical scroll.
//
// Without this, a paginated list on a screen either renders every loaded page at once — which works,
// but with no virtualisation — or, if given a LazyColumn of its own, makes Compose fail: it cannot
// measure the infinite height of a lazy list inside an already scrolling parent.
//
// So the root of the screen — always a column, which is what both screen builders produce — becomes
// the LazyColumn itself, and the top-level children become its items. A paginated list among them is
// the special case: its items become items of THAT SAME LazyColumn rather than a nested list.
@Composable
fun KompotLazyScreen(
    rootComponent: KompotComponent,
    registry: KompotRegistry,
    formController: FormController,
    actionHandler: KompotActionHandler,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    CompositionLocalProvider(LocalKompotRegistry provides registry) {
        val rootColumn = rootComponent as? ColumnComponent
        if (rootColumn == null) {
                // The root is not a column. This should not happen for either screen builder, but a
                // future DSL variant degrades to the ordinary, non-lazy render rather than breaking.
            Box(modifier = modifier.fillMaxSize().padding(contentPadding)) {
                KompotNode(registry, formController, rootComponent, actionHandler)
            }
        } else {
            val pageLoader = LocalKompotPageLoader.current
                // The pagination state for EVERY paginated list among the root's direct children is
                // computed here, in an ordinary composable body where remember and LaunchedEffect
                // work, rather than inside the LazyColumn builder below, where they cannot be.
            val paginatedStates =
                rootColumn.children.filterIsInstance<PaginatedListComponent>().associateWith { child ->
                    key(child.id) {
                        rememberPaginatedListState(child, pageLoader, formController)
                    }
                }

            LazyColumn(
                modifier = modifier.then(rootColumn.modifiers.toComposeModifier()),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(rootColumn.spacing.dp),
            ) {
                rootColumn.children.forEach { child ->
                    if (child is PaginatedListComponent) {
                        paginatedListItems(
                            component = child,
                            state = paginatedStates.getValue(child),
                            registry = registry,
                            actionHandler = actionHandler,
                            formController = formController,
                            pageLoader = pageLoader,
                        )
                    } else {
                        item(key = child.id) {
                            registry.RenderNode(child, actionHandler, formController)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UnknownComponentPlaceholder() {
    Text("Unknown component", color = MaterialTheme.colorScheme.error)
}

typealias RenderersMap = Map<KClass<out KompotComponent>, KompotComponentRenderer<out KompotComponent>>

class KompotRegistry(
    private val renderers: RenderersMap,
) {
    companion object {
        operator fun invoke(
            vararg renderers: RenderersMap,
            decorator: (RenderersMap) -> RenderersMap = { it },
        ): KompotRegistry =
            KompotRegistry(
                renderers.fold(mapOf()) { acc, renderer ->
                    acc + decorator(renderer)
                },
            )
    }

    @Composable
    fun <T : KompotComponent> RenderNode(
        component: T,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
            // A live update substitutes a node by id before dispatch — the single place this has to
            // be accounted for, and no component renderer knows the update channel exists. An update
            // may in principle change the type of a component, so the renderer lookup goes by
            // actual::class rather than by the static T.
        val realtimeUpdates = LocalKompotRealtimeUpdates.current
        val actual: KompotComponent = realtimeUpdates[component.id] ?: component
            // The registry keys renderers by the component's class, so it is the key that guarantees
            // the types line up, not the type system: a compiler cannot check this.
        @Suppress("UNCHECKED_CAST")
        val renderer = renderers[actual::class] as? KompotComponentRenderer<KompotComponent>

        if (renderer != null) {
            renderer.Render(actual, actionHandler, formController)
        } else {
            UnknownComponentPlaceholder()
        }
    }
}

// TextInputRenderer/AmountInputRenderer/CheckboxInputRenderer/AutocompleteInputRenderer/
// The select and radio-group renderers live in :kompot-forms-client, in this same package — see the
// note about the forms renderer map above.

// A tap on a container, or nothing at all when the server named no action. Written once rather than
// twice because row and column differ in axis and in nothing else here — and because the "no action
// means no clickable at all" half is the part worth not retyping: wrapping every container in a
// clickable with an empty lambda would give every row a ripple and a semantics node it has no
// business having.
@Composable
private fun Modifier.clickableWith(
    action: KompotAction?,
    actionHandler: KompotActionHandler,
): Modifier = if (action == null) this else clickable { actionHandler.handle(action) }
