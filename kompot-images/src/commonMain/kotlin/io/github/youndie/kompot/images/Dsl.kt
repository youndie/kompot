package io.github.youndie.kompot.images

import io.github.youndie.kompot.ColorToken
import io.github.youndie.kompot.dsl.KompotContainerContext
import io.github.youndie.kompot.dsl.KompotModifierBuilder
import kotlin.uuid.Uuid

public fun KompotContainerContext.image(
    url: String,
    contentDescription: String? = null,
    scaleType: ImageScaleType = ImageScaleType.Fit,
    tint: ColorToken? = null,
    id: String? = null,
    modifierBlock: (KompotModifierBuilder.() -> Unit)? = null,
) {
    val mods = modifierBlock?.let { KompotModifierBuilder().apply(it).build() } ?: emptyList()
    addComponent(
        KompotImageComponent(
            id = id ?: Uuid.random().toString(),
            url = url,
            contentDescription = contentDescription,
            scaleType = scaleType,
            tint = tint,
            modifiers = mods,
        ),
    )
}
