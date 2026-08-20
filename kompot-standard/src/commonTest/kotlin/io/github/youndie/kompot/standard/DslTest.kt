package io.github.youndie.kompot.standard

import io.github.youndie.kompot.KompotModifierNode
import io.github.youndie.kompot.ColorToken
import io.github.youndie.kompot.SizeType
import io.github.youndie.kompot.TypographyToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DslTest {
    @Test
    fun `column collects children in declaration order and defaults spacing to zero`() {
        val screen =
            kompotScreen {
                text("first")
                text("second")
            }

        assertEquals(0, screen.spacing)
        assertEquals(listOf("first", "second"), screen.children.map { (it as TextComponent).text })
    }

    @Test
    fun `kompotScreen always uses the fixed id root`() {
        val screen = kompotScreen { text("hi") }

        assertEquals("root", screen.id)
    }

    @Test
    fun `column without an explicit id generates a random one — different each time`() {
        val a = kompotScreen { column { text("a") } }.children.single() as ColumnComponent
        val b = kompotScreen { column { text("b") } }.children.single() as ColumnComponent

        assertNotEquals(a.id, b.id)
        assertTrue(a.id.isNotBlank())
    }

    @Test
    fun `column honors an explicit id instead of generating one`() {
        val screen = kompotScreen { column(id = "fixed") { text("a") } }

        assertEquals("fixed", (screen.children.single() as ColumnComponent).id)
    }

    @Test
    fun `column modifier and spacing blocks are applied to the built component`() {
        val screen =
            kompotScreen {
                column {
                    spacing(12)
                    modifier { background(ColorToken("surface")) }
                    text("a")
                }
            }

        val column = screen.children.single() as ColumnComponent
        assertEquals(12, column.spacing)
        val backgroundNode = column.modifiers.filterIsInstance<KompotModifierNode.Background>().single()
        assertEquals(ColorToken("surface"), backgroundNode.color)
    }

    @Test
    fun `row behaves like column but builds a RowComponent`() {
        val screen =
            kompotScreen {
                row {
                    spacing(8)
                    text("a")
                    text("b")
                }
            }

        val row = assertIs<RowComponent>(screen.children.single())
        assertEquals(8, row.spacing)
        assertEquals(2, row.children.size)
    }

    @Test
    fun `text defaults to no explicit style and carries no modifier unless one is passed`() {
        val screen = kompotScreen { text("hello") }

        val text = screen.children.single() as TextComponent
        assertEquals("hello", text.text)
        // null means the style was not set explicitly; kompot-standard is not tied to any design
        // system and must not decide for it which typography slot is the "default" (see Tokens.kt).
        assertNull(text.style)
        assertTrue(text.modifiers.isEmpty())
    }

    @Test
    fun `text style and modifierBlock are both honored`() {
        val screen =
            kompotScreen {
                text("hello", style = TypographyToken("headline_large"), modifierBlock = { fillMaxWidth() })
            }

        val text = screen.children.single() as TextComponent
        assertEquals(TypographyToken("headline_large"), text.style)
        val sizeNode = text.modifiers.filterIsInstance<KompotModifierNode.Size>().single()
        assertEquals(SizeType.Fill, sizeNode.width)
    }

    @Test
    fun `button carries the action through unchanged`() {
        val action = NavigateAction(deeplink = "app://home")
        val screen = kompotScreen { button(text = "Go", action = action) }

        val button = screen.children.single() as ButtonComponent
        assertEquals("Go", button.text)
        assertEquals(action, button.action)
    }

    @Test
    fun `table collects header and data rows in order`() {
        val screen =
            kompotScreen {
                table {
                    row("A", "B", header = true)
                    row("1", "2")
                }
            }

        val table = screen.children.single() as TableComponent
        assertEquals(2, table.rows.size)
        assertEquals(TableRow(listOf("A", "B"), header = true), table.rows[0])
        assertEquals(TableRow(listOf("1", "2"), header = false), table.rows[1])
    }

    @Test
    fun `paginatedList carries every optional field through to the component`() {
        val empty = TextComponent(id = "empty", text = "none")
        val next = LoadPageAction(url = "/items?page=2")
        val screen =
            kompotScreen {
                paginatedList(
                    initialItems = listOf(TextComponent(id = "item", text = "x")),
                    loadMoreAction = next,
                    reloadUrl = "/items",
                    emptyState = empty,
                )
            }

        val list = screen.children.single() as PaginatedListComponent
        assertEquals(1, list.initialItems.size)
        assertEquals(next, list.loadMoreAction)
        assertEquals("/items", list.reloadUrl)
        assertEquals(empty, list.emptyState)
    }

    @Test
    fun `paginatedList defaults loadMoreAction — reloadUrl and emptyState to null`() {
        val screen = kompotScreen { paginatedList(initialItems = emptyList()) }

        val list = screen.children.single() as PaginatedListComponent
        assertNull(list.loadMoreAction)
        assertNull(list.reloadUrl)
        assertNull(list.emptyState)
    }
}
