package io.github.youndie.kompot.standard

import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotModifierNode
import io.github.youndie.kompot.TypographyToken
import io.github.youndie.kompot.dsl.KompotContainerContext
import io.github.youndie.kompot.dsl.KompotDsl
import io.github.youndie.kompot.dsl.KompotModifierBuilder
import kotlin.uuid.Uuid

@KompotDsl
class ColumnBuilder(
    private val id: String?,
) : KompotContainerContext {
    private val children = mutableListOf<KompotComponent>()
    private var modifiers: List<KompotModifierNode> = emptyList()
    private var spacing: Int = 0

    fun modifier(block: KompotModifierBuilder.() -> Unit) {
        modifiers = KompotModifierBuilder().apply(block).build()
    }

    fun spacing(dp: Int) {
        spacing = dp
    }

    override fun addComponent(component: KompotComponent) {
        children.add(component)
    }

    fun build(): ColumnComponent =
        ColumnComponent(
            id = id ?: Uuid.random().toString(),
            modifiers = modifiers,
            spacing = spacing,
            children = children,
        )
}

fun KompotContainerContext.column(
    id: String? = null,
    block: ColumnBuilder.() -> Unit,
) {
    addComponent(ColumnBuilder(id).apply(block).build())
}

@KompotDsl
class RowBuilder(
    private val id: String?,
) : KompotContainerContext {
    private val children = mutableListOf<KompotComponent>()
    private var modifiers: List<KompotModifierNode> = emptyList()
    private var spacing: Int = 0

    fun modifier(block: KompotModifierBuilder.() -> Unit) {
        modifiers = KompotModifierBuilder().apply(block).build()
    }

    fun spacing(dp: Int) {
        spacing = dp
    }

    override fun addComponent(component: KompotComponent) {
        children.add(component)
    }

    fun build(): RowComponent =
        RowComponent(
            id = id ?: Uuid.random().toString(),
            modifiers = modifiers,
            spacing = spacing,
            children = children,
        )
}

fun KompotContainerContext.row(
    id: String? = null,
    block: RowBuilder.() -> Unit,
) {
    addComponent(RowBuilder(id).apply(block).build())
}

fun KompotContainerContext.text(
    text: String,
    style: TypographyToken? = null,
    id: String? = null,
    modifierBlock: (KompotModifierBuilder.() -> Unit)? = null,
) {
    val mods = modifierBlock?.let { KompotModifierBuilder().apply(it).build() } ?: emptyList()
    addComponent(TextComponent(id ?: Uuid.random().toString(), mods, text, style))
}

fun KompotContainerContext.button(
    text: String,
    action: KompotAction,
    id: String? = null,
    modifierBlock: (KompotModifierBuilder.() -> Unit)? = null,
) {
    val mods = modifierBlock?.let { KompotModifierBuilder().apply(it).build() } ?: emptyList()
    addComponent(ButtonComponent(id ?: Uuid.random().toString(), mods, text, action))
}

@KompotDsl
class TableBuilder {
    private val rows = mutableListOf<TableRow>()

    fun row(
        vararg cells: String,
        header: Boolean = false,
    ) {
        rows += TableRow(cells.toList(), header)
    }

    fun build(): List<TableRow> = rows.toList()
}

fun KompotContainerContext.table(
    id: String? = null,
    modifierBlock: (KompotModifierBuilder.() -> Unit)? = null,
    block: TableBuilder.() -> Unit,
) {
    val mods = modifierBlock?.let { KompotModifierBuilder().apply(it).build() } ?: emptyList()
    addComponent(TableComponent(id ?: Uuid.random().toString(), mods, TableBuilder().apply(block).build()))
}

fun KompotContainerContext.paginatedList(
    initialItems: List<KompotComponent>,
    loadMoreAction: LoadPageAction? = null,
    reloadUrl: String? = null,
    emptyState: KompotComponent? = null,
    id: String? = null,
    modifierBlock: (KompotModifierBuilder.() -> Unit)? = null,
) {
    val mods = modifierBlock?.let { KompotModifierBuilder().apply(it).build() } ?: emptyList()
    addComponent(
        PaginatedListComponent(
            id = id ?: Uuid.random().toString(),
            modifiers = mods,
            initialItems = initialItems,
            loadMoreAction = loadMoreAction,
            reloadUrl = reloadUrl,
            emptyState = emptyState,
        ),
    )
}

fun kompotScreen(block: ColumnBuilder.() -> Unit): ColumnComponent = ColumnBuilder(id = "root").apply(block).build()
