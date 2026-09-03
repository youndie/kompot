package io.github.youndie.kompot.forms.standard

import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotModifierNode
import io.github.youndie.kompot.dsl.KompotContainerContext
import io.github.youndie.kompot.dsl.KompotDsl
import io.github.youndie.kompot.dsl.KompotModifierBuilder
import io.github.youndie.kompot.forms.KompotFormResponse
import io.github.youndie.kompot.standard.ColumnComponent
import io.github.youndie.kompot.standard.ROOT_PATH
import io.github.youndie.kompot.standard.RowComponent
import io.github.youndie.kompot.form.FormFieldDefinition
import io.github.youndie.kompot.form.FormSchema
import io.github.youndie.kompot.form.ValidationRule
import io.github.youndie.kompot.form.ValidationRulesBuilder

// An extension of KompotContainerContext that adds field(...), the entry point for the bound builders
// in BoundFields.kt. The screen is still built exactly as before — columns, text, buttons, unbound
// inputs — because this context is still a KompotContainerContext; what appears is the ability to
// declare a schema field alongside, without leaving the current level of nesting.
@KompotDsl
public interface KompotFormContext : KompotContainerContext {
    public fun field(definition: FormFieldDefinition)
}

// The glue builder: the screen and the form schema are assembled by one buildFormScreen { ... } call,
// so a fieldId is declared once and cannot drift between the UI tree and the validation schema. The
// wire contract is unchanged — schema and screen come out as the same two independent halves, just
// laid out by the builder instead of by hand.
//
// This is the structural answer to SPEC.md §9.2: connectivity that a rule can only ask for, a DSL can
// make unrepresentable.
@KompotDsl
public class KompotFormBuilder(
    private val formId: String,
) : KompotFormContext {
    private val children = mutableListOf<KompotComponent>()
    private val fields = mutableListOf<FormFieldDefinition>()
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

    override fun field(definition: FormFieldDefinition) {
        fields += definition
    }

    public fun build(): KompotFormResponse {
        val schema = FormSchema(formId = formId, fields = fields.toList())
        val screen =
            ColumnComponent(
                id = "root_$formId",
                modifiers = modifiers,
                spacing = spacing,
                children = children.toList(),
            )
        return KompotFormResponse(schema = schema, screen = screen)
    }
}

public fun buildFormScreen(
    formId: String,
    block: KompotFormBuilder.() -> Unit,
): KompotFormResponse = KompotFormBuilder(formId).apply(block).build()

// Counterparts of the standard column and row builders that forward field() to their parent, so the
// bound builders work inside column { } / row { } rather than only at the top level of a form.
@KompotDsl
public class FormColumnBuilder(
    private val parent: KompotFormContext,
    private val id: String?,
    private val path: String = id ?: ROOT_PATH,
) : KompotFormContext {
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

    override fun field(definition: FormFieldDefinition) {
        parent.field(definition)
    }

    override fun nextChildPath(): String = "$path/${children.size}"

    public fun build(): ColumnComponent =
        ColumnComponent(id = id ?: path, modifiers = modifiers, spacing = spacing, children = children.toList())
}

public fun KompotFormContext.column(
    id: String? = null,
    block: FormColumnBuilder.() -> Unit,
) {
    addComponent(FormColumnBuilder(this, id, id ?: nextChildPath()).apply(block).build())
}

@KompotDsl
public class FormRowBuilder(
    private val parent: KompotFormContext,
    private val id: String?,
    private val path: String = id ?: ROOT_PATH,
) : KompotFormContext {
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

    override fun field(definition: FormFieldDefinition) {
        parent.field(definition)
    }

    override fun nextChildPath(): String = "$path/${children.size}"

    public fun build(): RowComponent =
        RowComponent(id = id ?: path, modifiers = modifiers, spacing = spacing, children = children.toList())
}

public fun KompotFormContext.row(
    id: String? = null,
    block: FormRowBuilder.() -> Unit,
) {
    addComponent(FormRowBuilder(this, id, id ?: nextChildPath()).apply(block).build())
}

internal fun buildRules(rules: ValidationRulesBuilder.() -> Unit): List<ValidationRule> = ValidationRulesBuilder().apply(rules).build()
