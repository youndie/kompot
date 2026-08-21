package io.github.youndie.kompot.forms.standard

import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.RowComponent
import io.github.youndie.kompot.standard.TextComponent
import io.github.youndie.kompot.standard.text
import io.github.youndie.kompot.form.FormFieldDefinition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private data class FakeField(
    override val fieldId: String,
) : FormFieldDefinition {
    override val rules = emptyList<Nothing>()
}

class KompotFormBuilderTest {
    @Test
    fun `the screen root is a column named after the formId, and free UI still works via KompotContainerContext`() {
        val response =
            buildFormScreen("catalogue_filters") {
                text("Filters")
            }

        assertEquals("root_catalogue_filters", response.screen.id)
        assertIs<ColumnComponent>(response.screen)
        assertEquals("Filters", (response.screen as ColumnComponent).children.single().let { it as TextComponent }.text)
    }

    @Test
    fun `fields declared at the top level land in the schema in declaration order`() {
        val response =
            buildFormScreen("form_1") {
                field(FakeField("a"))
                field(FakeField("b"))
            }

        assertEquals(listOf("a", "b"), response.schema.fields.map { it.fieldId })
        assertEquals("form_1", response.schema.formId)
    }

    @Test
    fun `fields declared inside a nested column still land in the SAME top-level schema`() {
        val response =
            buildFormScreen("form_1") {
                column {
                    field(FakeField("nested"))
                }
                field(FakeField("top_level"))
            }

        assertEquals(listOf("nested", "top_level"), response.schema.fields.map { it.fieldId })
    }

    @Test
    fun `fields declared inside a nested row also propagate to the parent schema`() {
        val response =
            buildFormScreen("form_1") {
                row {
                    field(FakeField("in_row"))
                }
            }

        assertEquals(listOf("in_row"), response.schema.fields.map { it.fieldId })
    }

    @Test
    fun `fields propagate through arbitrarily deep nesting (column inside row inside column)`() {
        val response =
            buildFormScreen("form_1") {
                column {
                    row {
                        column {
                            field(FakeField("deep"))
                        }
                    }
                }
            }

        assertEquals(listOf("deep"), response.schema.fields.map { it.fieldId })
    }

    @Test
    fun `column and row build their own UI subtree independently of the schema`() {
        val response =
            buildFormScreen("form_1") {
                column {
                    spacing(8)
                    text("in column")
                }
                row {
                    spacing(4)
                    text("in row")
                }
            }

        val screen = response.screen as ColumnComponent
        val nestedColumn = screen.children[0] as ColumnComponent
        val nestedRow = screen.children[1] as RowComponent
        assertEquals(8, nestedColumn.spacing)
        assertEquals(4, nestedRow.spacing)
        assertTrue(nestedColumn.children.single() is TextComponent)
        assertTrue(nestedRow.children.single() is TextComponent)
    }
}
