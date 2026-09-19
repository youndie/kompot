package io.github.youndie.kompot.playground.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.youndie.kompot.KompotActionHandler
import io.github.youndie.kompot.KompotComponentRenderer
import io.github.youndie.kompot.RenderersMap
import io.github.youndie.kompot.form.FormController
import io.github.youndie.kompot.toComposeModifier

// The other half of a plug-in: how the deployment draws its own type.
//
// A client assembled without this map still DECODES the banner — the registry decides what is drawn,
// the serializers module decides what is understood, and the two halves fail differently (SPEC.md
// §2.1 against UNRENDERABLE_COMPONENT). B-29 leans on exactly that difference.
public class PromoBannerRenderer : KompotComponentRenderer<PromoBanner> {
    @Composable
    override fun Render(
        component: PromoBanner,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        Card(
            modifier = component.modifiers.toComposeModifier().fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(component.title, style = MaterialTheme.typography.titleLarge)
                Text(component.text, style = MaterialTheme.typography.bodyMedium)
                // The tap goes nowhere: the page has no server, and a button that pretended to act
                // would be the one dishonest pixel on it.
                Button(onClick = {}) { Text(component.cta) }
            }
        }
    }
}

public val demoRenderers: RenderersMap = mapOf(PromoBanner::class to PromoBannerRenderer())
