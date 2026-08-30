package io.github.youndie.kompot.wizard

import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.dsl.KompotModifierBuilder
import kotlin.uuid.Uuid

// A standalone factory rather than an extension of KompotContainerContext, the way most component
// builders are: a WizardScreenComponent is always the root of a whole step screen, never a node inside
// someone else's tree.
public fun wizardScreen(
    formId: String,
    stepId: String,
    stepIndex: Int,
    content: KompotComponent,
    totalSteps: Int? = null,
    canGoBack: Boolean = false,
    id: String? = null,
    modifierBlock: (KompotModifierBuilder.() -> Unit)? = null,
): WizardScreenComponent {
    val mods = modifierBlock?.let { KompotModifierBuilder().apply(it).build() } ?: emptyList()
    return WizardScreenComponent(
        id = id ?: Uuid.random().toString(),
        modifiers = mods,
        formId = formId,
        stepId = stepId,
        stepIndex = stepIndex,
        totalSteps = totalSteps,
        canGoBack = canGoBack,
        content = content,
    )
}
