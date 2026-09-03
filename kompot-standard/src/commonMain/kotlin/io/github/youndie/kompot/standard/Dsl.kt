package io.github.youndie.kompot.standard

import io.github.youndie.kompot.ColorToken
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotModifierNode
import io.github.youndie.kompot.TypographyToken
import io.github.youndie.kompot.dsl.KompotContainerContext
import io.github.youndie.kompot.dsl.KompotDsl
import io.github.youndie.kompot.dsl.KompotModifierBuilder

@KompotDsl
public class ColumnBuilder(
    private val id: String?,
    // Where this node sits in the tree, for naming the children nobody named. The node's own id when
    // it has one, so a named subtree keeps its name in every path below it.
    private val path: String = id ?: ROOT_PATH,
) : KompotContainerContext {
    private val children = mutableListOf<KompotComponent>()
    private var modifiers: List<KompotModifierNode> = emptyList()
    private var spacing: Int = 0

    public fun modifier(block: KompotModifierBuilder.() -> Unit) {
        modifiers = KompotModifierBuilder().apply(block).build()
    }

    public fun spacing(dp: Int) {
        spacing = dp
    }

    override fun addComponent(component: KompotComponent) {
        children.add(component)
    }

    override fun nextChildPath(): String = "$path/${children.size}"

    public fun build(): ColumnComponent =
        ColumnComponent(
            id = id ?: path,
            modifiers = modifiers,
            spacing = spacing,
            children = children,
        )
}

public fun KompotContainerContext.column(
    id: String? = null,
    block: ColumnBuilder.() -> Unit,
) {
    // The path is taken BEFORE the block runs, so the children it adds are numbered under this node
    // rather than under its parent.
    addComponent(ColumnBuilder(id, id ?: nextChildPath()).apply(block).build())
}

@KompotDsl
public class RowBuilder(
    private val id: String?,
    private val path: String = id ?: ROOT_PATH,
) : KompotContainerContext {
    private val children = mutableListOf<KompotComponent>()
    private var modifiers: List<KompotModifierNode> = emptyList()
    private var spacing: Int = 0

    public fun modifier(block: KompotModifierBuilder.() -> Unit) {
        modifiers = KompotModifierBuilder().apply(block).build()
    }

    public fun spacing(dp: Int) {
        spacing = dp
    }

    override fun addComponent(component: KompotComponent) {
        children.add(component)
    }

    override fun nextChildPath(): String = "$path/${children.size}"

    public fun build(): RowComponent =
        RowComponent(
            id = id ?: path,
            modifiers = modifiers,
            spacing = spacing,
            children = children,
        )
}

public fun KompotContainerContext.row(
    id: String? = null,
    block: RowBuilder.() -> Unit,
) {
    addComponent(RowBuilder(id, id ?: nextChildPath()).apply(block).build())
}

public fun KompotContainerContext.text(
    text: String,
    style: TypographyToken? = null,
    color: ColorToken? = null,
    id: String? = null,
    modifierBlock: (KompotModifierBuilder.() -> Unit)? = null,
) {
    val mods = modifierBlock?.let { KompotModifierBuilder().apply(it).build() } ?: emptyList()
    addComponent(TextComponent(id ?: nextChildPath(), mods, text, style, color))
}

public fun KompotContainerContext.button(
    text: String,
    action: KompotAction,
    id: String? = null,
    modifierBlock: (KompotModifierBuilder.() -> Unit)? = null,
) {
    val mods = modifierBlock?.let { KompotModifierBuilder().apply(it).build() } ?: emptyList()
    addComponent(ButtonComponent(id ?: nextChildPath(), mods, text, action))
}

@KompotDsl
public class TableBuilder {
    private val rows = mutableListOf<TableRow>()

    public fun row(
        vararg cells: String,
        header: Boolean = false,
    ) {
        rows += TableRow(cells.toList(), header)
    }

    public fun build(): List<TableRow> = rows.toList()
}

public fun KompotContainerContext.table(
    id: String? = null,
    modifierBlock: (KompotModifierBuilder.() -> Unit)? = null,
    block: TableBuilder.() -> Unit,
) {
    val mods = modifierBlock?.let { KompotModifierBuilder().apply(it).build() } ?: emptyList()
    addComponent(TableComponent(id ?: nextChildPath(), mods, TableBuilder().apply(block).build()))
}

public fun KompotContainerContext.paginatedList(
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
            id = id ?: nextChildPath(),
            modifiers = mods,
            initialItems = initialItems,
            loadMoreAction = loadMoreAction,
            reloadUrl = reloadUrl,
            emptyState = emptyState,
        ),
    )
}

// "root", which the screen's own node has always been called, and the prefix every unnamed node below
// it is numbered under.
public const val ROOT_PATH: String = "root"

public fun kompotScreen(block: ColumnBuilder.() -> Unit): ColumnComponent =
    ColumnBuilder(id = ROOT_PATH, path = ROOT_PATH).apply(block).build()
