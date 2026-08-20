package io.github.youndie.kompot.wizard.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// A branching graph: an amount over the threshold routes through an approval step, a smaller one goes
// straight to confirmation. The draft is a plain Map<String, Long> with no dependency on any form,
// component or saga type — the whole graph is covered here by pure unit tests, with no HTTP server and
// no UI framework.
private const val STEP_DETAILS = "details"
private const val STEP_APPROVAL = "approval"
private const val STEP_CONFIRMATION = "confirmation"
private const val APPROVAL_THRESHOLD = 1_000_000L

private val orderResolver =
    WizardStepResolver<Map<String, Long>> { currentStepId, draft ->
        when (currentStepId) {
            STEP_DETAILS ->
                if ((draft["amount"] ?: 0L) > APPROVAL_THRESHOLD) STEP_APPROVAL else STEP_CONFIRMATION
            STEP_APPROVAL -> STEP_CONFIRMATION
            STEP_CONFIRMATION -> null
            else -> null
        }
    }

private fun orderEngine() = WizardEngine(initialStepId = STEP_DETAILS, stepResolver = orderResolver)

class WizardEngineTest {
    @Test
    fun `start places the session on the initial step with an empty history`() {
        val session = orderEngine().start(emptyMap())

        assertEquals(STEP_DETAILS, session.currentStepId)
        assertEquals(emptyList(), session.history)
        assertFalse(session.isFinished)
    }

    @Test
    fun `a small order skips approval and goes straight to confirmation`() {
        val engine = orderEngine()
        val session = engine.start(emptyMap())

        val afterDetails = engine.transition(session, WizardTransition.Next, mapOf("amount" to 500_000L))

        assertEquals(STEP_CONFIRMATION, afterDetails.currentStepId)
        assertEquals(listOf(STEP_DETAILS), afterDetails.history)
        assertFalse(afterDetails.isFinished)
    }

    @Test
    fun `a large order routes through the approval step`() {
        val engine = orderEngine()
        val session = engine.start(emptyMap())

        val afterDetails = engine.transition(session, WizardTransition.Next, mapOf("amount" to 2_000_000L))
        assertEquals(STEP_APPROVAL, afterDetails.currentStepId)

        val afterApproval = engine.transition(afterDetails, WizardTransition.Next, afterDetails.draft + ("approval" to 1L))
        assertEquals(STEP_CONFIRMATION, afterApproval.currentStepId)
        assertEquals(listOf(STEP_DETAILS, STEP_APPROVAL), afterApproval.history)
    }

    @Test
    fun `Next on the last step of the graph stays there without auto-finishing`() {
        // "The resolver returned null" and "the wizard is over" are different things: on a final step —
        // normally a review or confirmation — the user must be able to press Back before finishing for
        // real with an explicit Finish (see the next test and the Back regression below).
        val engine = orderEngine()
        val session = engine.start(mapOf("amount" to 500_000L))

        val afterDetails = engine.transition(session, WizardTransition.Next, session.draft)
        assertEquals(STEP_CONFIRMATION, afterDetails.currentStepId)
        assertFalse(afterDetails.isFinished)

        val afterConfirmation = engine.transition(afterDetails, WizardTransition.Next, afterDetails.draft)
        assertFalse(afterConfirmation.isFinished)
        assertEquals(STEP_CONFIRMATION, afterConfirmation.currentStepId)
    }

    @Test
    fun `Finish on the last step of the graph actually finishes the wizard`() {
        val engine = orderEngine()
        val session = engine.start(mapOf("amount" to 500_000L))
        val onConfirmation = engine.transition(session, WizardTransition.Next, session.draft)

        val finished = engine.transition(onConfirmation, WizardTransition.Finish, onConfirmation.draft)

        assertTrue(finished.isFinished)
        assertEquals(STEP_CONFIRMATION, finished.currentStepId)
    }

    @Test
    fun `an explicit Finish transition ends the wizard immediately regardless of the resolver`() {
        val engine = orderEngine()
        val session = engine.start(mapOf("amount" to 2_000_000L))

        val finished = engine.transition(session, WizardTransition.Finish, session.draft)

        assertTrue(finished.isFinished)
        // Finish does not consult the resolver: it stays on the step it was called from.
        assertEquals(STEP_DETAILS, finished.currentStepId)
    }

    @Test
    fun `Back returns to the previous step and pops it off the history stack`() {
        val engine = orderEngine()
        val session = engine.start(emptyMap())
        val afterDetails = engine.transition(session, WizardTransition.Next, mapOf("amount" to 2_000_000L))
        val afterApproval = engine.transition(afterDetails, WizardTransition.Next, afterDetails.draft)

        val back = engine.transition(afterApproval, WizardTransition.Back, afterApproval.draft)

        assertEquals(STEP_APPROVAL, back.currentStepId)
        assertEquals(listOf(STEP_DETAILS), back.history)
    }

    @Test
    fun `Back on the very first step with empty history stays in place instead of throwing`() {
        val engine = orderEngine()
        val session = engine.start(emptyMap())

        val back = engine.transition(session, WizardTransition.Back, mapOf("amount" to 10L))

        assertEquals(STEP_DETAILS, back.currentStepId)
        assertEquals(emptyList(), back.history)
        // Data entered before Back was pressed is not lost silently.
        assertEquals(mapOf("amount" to 10L), back.draft)
    }

    @Test
    fun `Back after routing through approval returns to details — not confirmation`() {
        // A regression guard for "the resolver recomputes the route instead of the history being used":
        // if Back consulted the resolver rather than the history stack, then with a fresh draft (the
        // amount is already large) it could "return" the user somewhere they never came from.
        val engine = orderEngine()
        val session = engine.start(emptyMap())
        val afterDetails = engine.transition(session, WizardTransition.Next, mapOf("amount" to 2_000_000L))
        val afterApproval = engine.transition(afterDetails, WizardTransition.Next, afterDetails.draft)
        val afterConfirmation = engine.transition(afterApproval, WizardTransition.Next, afterApproval.draft)

        val backOnce = engine.transition(afterConfirmation, WizardTransition.Back, afterConfirmation.draft)
        assertEquals(STEP_APPROVAL, backOnce.currentStepId)

        val backTwice = engine.transition(backOnce, WizardTransition.Back, backOnce.draft)
        assertEquals(STEP_DETAILS, backTwice.currentStepId)
        assertEquals(emptyList(), backTwice.history)
    }

    @Test
    fun `JumpTo moves directly to an arbitrary step and still records history for Back`() {
        val engine = orderEngine()
        val session = engine.start(emptyMap())

        val jumped = engine.transition(session, WizardTransition.JumpTo(STEP_CONFIRMATION), mapOf("amount" to 500_000L))

        assertEquals(STEP_CONFIRMATION, jumped.currentStepId)
        assertEquals(listOf(STEP_DETAILS), jumped.history)

        val back = engine.transition(jumped, WizardTransition.Back, jumped.draft)
        assertEquals(STEP_DETAILS, back.currentStepId)
    }

    @Test
    fun `once finished — further transitions only update the draft and never move steps again`() {
        val engine = orderEngine()
        val session = engine.start(mapOf("amount" to 500_000L))
        val finished = engine.transition(session, WizardTransition.Finish, session.draft)

        val afterNext = engine.transition(finished, WizardTransition.Next, mapOf("amount" to 999L))
        assertTrue(afterNext.isFinished)
        assertEquals(STEP_DETAILS, afterNext.currentStepId)
        assertEquals(mapOf("amount" to 999L), afterNext.draft)
    }
}
