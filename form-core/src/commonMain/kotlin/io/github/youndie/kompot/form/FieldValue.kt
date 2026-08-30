package io.github.youndie.kompot.form

// The open contract for a field's value. Concrete representations — text, amount, checkbox and so
// on — are defined in plug-in modules (:form-standard, for one) and registered in the application's
// SerializersModule through polymorphic(FieldValue::class) { subclass(...) }.
public interface FieldValue {
    // The value as one plain string: what a filter sends as a query parameter, and what any flat
    // key/value payload carries.
    //
    // It lives here, on the contract, because the alternative is a `when` over concrete types
    // somewhere in the toolkit — and that is the toolkit knowing an application's field plug-in.
    // A type that adds a value knows how to flatten it; nobody else does.
    //
    // The default is deliberately a last resort rather than an abstract member: a value that never
    // travels this way should not be forced to invent an answer, and an existing plug-in should not
    // stop compiling because this appeared.
    public val plainValue: String get() = toString()
}
