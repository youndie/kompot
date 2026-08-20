package io.github.youndie.kompot.form

// The open contract for a field's value. Concrete representations — text, amount, checkbox and so
// on — are defined in plug-in modules (:form-standard, for one) and registered in the application's
// SerializersModule through polymorphic(FieldValue::class) { subclass(...) }.
interface FieldValue
