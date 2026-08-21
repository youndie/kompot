package io.github.youndie.kompot

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.youndie.kompot.registry.KompotComponentMarker
import io.github.youndie.kompot.wizard.PrevStepAction
import io.github.youndie.kompot.wizard.WizardScreenComponent
import io.github.youndie.kompot.form.FormController

// The wrapper renderer of a wizard step. It draws no content of its own — that is delegated to
// registry.RenderNode — only the chrome: a Back button when canGoBack, and a progress indicator when
// totalSteps is known.
//
// Moving forward is deliberately NOT a button of this renderer: Next comes from the step's own
// content, usually a form whose submit action is a NextStepAction. That way Enter on a mobile
// keyboard advances the flow without any extra UI element.
@KompotComponentMarker
class WizardScreenRenderer : KompotComponentRenderer<WizardScreenComponent> {
    @Composable
    override fun Render(
        component: WizardScreenComponent,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        val registry = LocalKompotRegistry.current
        Column(modifier = component.modifiers.toComposeModifier().fillMaxWidth()) {
            if (component.canGoBack) {
                Row(
                    modifier =
                        Modifier
                            .clickable { actionHandler.handle(PrevStepAction(component.formId)) }
                            .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "←", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Back",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }

            val totalSteps = component.totalSteps
            if (totalSteps != null && totalSteps > 0) {
                LinearProgressIndicator(
                    progress = { (component.stepIndex + 1).toFloat() / totalSteps },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                )
            }

            registry.RenderNode(
                component = component.content,
                actionHandler = actionHandler,
                formController = formController,
            )
        }
    }
}
