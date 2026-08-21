package io.github.youndie.kompot.images.coil

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ColorFilter
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import io.github.youndie.kompot.KompotActionHandler
import io.github.youndie.kompot.KompotComponentRenderer
import io.github.youndie.kompot.LocalKompotDesignSystem
import io.github.youndie.kompot.toComposeModifier
import io.github.youndie.kompot.images.KompotImageComponent
import io.github.youndie.kompot.images.ImageScaleType
import io.github.youndie.kompot.registry.KompotComponentMarker
import io.github.youndie.kompot.form.FormController
import androidx.compose.ui.layout.ContentScale
import coil3.compose.LocalPlatformContext

// There is no hand-written renderer map here: :kompot-registry-processor generates it as
// generatedImagesClientRenderers (see io.github.youndie.kompot.generated).
@KompotComponentMarker
class KompotImageRenderer : KompotComponentRenderer<KompotImageComponent> {
    @Composable
    override fun Render(
        component: KompotImageComponent,
        actionHandler: KompotActionHandler,
        formController: FormController,
    ) {
        val designSystem = LocalKompotDesignSystem.current
        val tint = component.tint?.let { ColorFilter.tint(designSystem.resolveColor(it)) }

        AsyncImage(
            model =
                ImageRequest
                    .Builder(LocalPlatformContext.current)
                    .data(component.url)
                    .build(),
            contentDescription = component.contentDescription,
            modifier = component.modifiers.toComposeModifier(),
            contentScale =
                when (component.scaleType) {
                    ImageScaleType.Crop -> ContentScale.Crop
                    ImageScaleType.Fit -> ContentScale.Fit
                    ImageScaleType.FillBounds -> ContentScale.FillBounds
                },
            colorFilter = tint,
        )
    }
}
