package io.github.youndie.kompot.form.standard

import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import io.github.youndie.kompot.form.FieldValue
import io.github.youndie.kompot.form.FormCondition
import io.github.youndie.kompot.form.FormFieldDefinition
import io.github.youndie.kompot.form.ValidationRule

// The serialisation "plug-in" for the standard field set. An application merges this module into its
// own SerializersModule so that kotlinx.serialization can (de)serialise the open interfaces of
// form-core into these concrete implementations.
val formStandardSerializersModule =
    SerializersModule {
        polymorphic(FormFieldDefinition::class) {
            subclass(TextFieldDefinition::class)
            subclass(AmountFieldDefinition::class)
            subclass(CheckboxFieldDefinition::class)
            subclass(AutocompleteFieldDefinition::class)
            subclass(SelectionFieldDefinition::class)
        }

        polymorphic(ValidationRule::class) {
            subclass(RequiredRule::class)
            subclass(RegexRule::class)
            subclass(RequiredIfRule::class)
            subclass(MaxAmountRule::class)
        }

        polymorphic(FieldValue::class) {
            subclass(TextValue::class)
            subclass(AmountValue::class)
            subclass(BooleanValue::class)
            subclass(EntityValue::class)
        }

        polymorphic(FormCondition::class) {
            subclass(EqualsCondition::class)
            subclass(NotEqualsCondition::class)
        }
    }
