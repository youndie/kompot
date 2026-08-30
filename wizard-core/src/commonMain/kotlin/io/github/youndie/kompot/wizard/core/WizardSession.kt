package io.github.youndie.kompot.wizard.core

// The state of one wizard session. `history` is the stack of PREVIOUS step ids — it does not include
// currentStepId — and exists solely for Back: going back may only lead where the user actually came
// from, not where the resolver would route now, since the route may have depended on a draft that has
// changed since. `draft` is the data accumulated so far; the type T is deliberately unconstrained and
// tied to no form or component model, so an entire transition graph can be covered by unit tests with
// any simple T — a Map<String, Int>, a data class, whatever — with no HTTP server, database or UI
// framework in sight (see WizardEngineTest).
public data class WizardSession<T>(
    val currentStepId: String,
    val history: List<String> = emptyList(),
    val draft: T,
    val isFinished: Boolean = false,
)
