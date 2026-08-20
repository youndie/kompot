package io.github.youndie.kompot.wizard.core

// Computes the id of the next step for a Next transition from the current step and the ACCUMULATED
// draft — after the values of the current step have been merged into it. This is where the graph
// branches: an amount over some threshold routes through an extra step, a smaller one goes straight
// on. Returning null means "this is the last step of the graph", and Next simply stays put; the user
// has to confirm explicitly with Finish. "No next step" and "the wizard is over" are different things:
// on a final step there is usually something to review, and a Back to take, before actually
// submitting.
fun interface WizardStepResolver<T> {
    fun resolveNext(
        currentStepId: String,
        draft: T,
    ): String?
}

// The state machine itself. It stores no state — WizardSession is a plain immutable value — and every
// transition() call is a pure function (session, transition, draft) -> new session. That is what makes
// a wizard graph of any size and complexity checkable by ordinary unit tests without a single trip to
// a database or the network.
class WizardEngine<T>(
    private val initialStepId: String,
    private val stepResolver: WizardStepResolver<T>,
) {
    fun start(initialDraft: T): WizardSession<T> = WizardSession(currentStepId = initialStepId, draft = initialDraft)

    // updatedDraft is the draft that ALREADY carries the values entered on currentStepId — merging them
    // is the caller's responsibility. The engine merges nothing and knows nothing about what is inside T.
    fun transition(
        session: WizardSession<T>,
        transition: WizardTransition,
        updatedDraft: T,
    ): WizardSession<T> {
        if (session.isFinished) return session.copy(draft = updatedDraft)

        return when (transition) {
            WizardTransition.Next -> {
                val next = stepResolver.resolveNext(session.currentStepId, updatedDraft)
                if (next == null) {
                    // The last step of the graph: stay put, but keep the data anyway. isFinished is
                    // deliberately NOT set here (see the comment on WizardStepResolver).
                    session.copy(draft = updatedDraft)
                } else {
                    session.copy(
                        currentStepId = next,
                        history = session.history + session.currentStepId,
                        draft = updatedDraft,
                    )
                }
            }

            WizardTransition.Back -> {
                val previous = session.history.lastOrNull()
                if (previous == null) {
                    // Nowhere to go back to — already on the first step. Stay put, but keep the data
                    // entered on the current step rather than dropping it silently.
                    session.copy(draft = updatedDraft)
                } else {
                    session.copy(
                        currentStepId = previous,
                        history = session.history.dropLast(1),
                        draft = updatedDraft,
                    )
                }
            }

            is WizardTransition.JumpTo -> {
                session.copy(
                    currentStepId = transition.stepId,
                    history = session.history + session.currentStepId,
                    draft = updatedDraft,
                )
            }

            WizardTransition.Finish -> session.copy(draft = updatedDraft, isFinished = true)
        }
    }
}
