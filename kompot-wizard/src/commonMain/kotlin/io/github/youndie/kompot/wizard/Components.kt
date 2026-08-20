package io.github.youndie.kompot.wizard

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import io.github.youndie.kompot.KompotAction
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotModifierNode
import io.github.youndie.kompot.registry.KompotComponentMarker
import io.github.youndie.kompot.form.FormSchema

// The container of one wizard step. Not the content of the step — that arrives fully built, as any
// KompotComponent, typically the tree of an ordinary form screen — but a wrapper around it carrying
// progress metadata: stepIndex/totalSteps for the indicator at the top, and canGoBack, false on the
// first step where there is nowhere to go back to (see history in WizardSession, :wizard-core).
//
// totalSteps is nullable: a branching graph can change the length of a particular run, so the exact
// number of steps is not always known in advance. null means "show only the current step, not a total".
//
// formId is the same id as the FormSchema of the content inside. A renderer cannot dig it out of
// `content` itself — that is a KompotComponent, a bare tree rather than a whole form response — yet it
// needs the id to build NextStepAction(formId)/PrevStepAction(formId) for its own Back button and
// progress indicator.
@Serializable
@SerialName("wizard_screen")
@KompotComponentMarker
data class WizardScreenComponent(
    override val id: String,
    override val modifiers: List<KompotModifierNode> = emptyList(),
    val formId: String,
    val stepId: String,
    val stepIndex: Int,
    val totalSteps: Int? = null,
    val canGoBack: Boolean = false,
    val content: @Polymorphic KompotComponent,
) : KompotComponent

// The three wizard actions, mirroring SubmitFormAction(formId) in :kompot-forms: a formId rather than
// an arbitrary payload, because the actual field values travel separately in a form-patch request. It
// is the same pattern as an ordinary form submit — the action here is only a SIGNAL of which
// transition to make.
@Serializable
@SerialName("wizard_next")
data class NextStepAction(
    val formId: String,
) : KompotAction

@Serializable
@SerialName("wizard_back")
data class PrevStepAction(
    val formId: String,
) : KompotAction

@Serializable
@SerialName("wizard_finish")
data class FinishWizardAction(
    val formId: String,
) : KompotAction

// The server's answer to a Next/Prev/FinishWizardAction while the wizard is still running — the
// application builds it from the form screen of the step plus a wizardScreen{} wrapper. It is itself a
// KompotAction rather than an HTTP envelope of its own, so the result of a resume passes through THE
// SAME handler chain as any other action and needs no extra protocol on top. Once the wizard is
// finished the server answers with any OTHER action — usually a navigation one — and this type is not
// involved at all.
@Serializable
@SerialName("wizard_step_result")
data class WizardStepAction(
    val formId: String,
    val schema: FormSchema,
    val screen: @Polymorphic KompotComponent,
) : KompotAction
