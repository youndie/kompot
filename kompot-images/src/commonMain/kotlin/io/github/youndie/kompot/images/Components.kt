package io.github.youndie.kompot.images

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotModifierNode
import io.github.youndie.kompot.ColorToken
import io.github.youndie.kompot.registry.KompotComponentMarker

// A content image. `url` is the single source of the picture: design-system SVG icons served as
// versioned static files (ic_home_v2.svg and the like) and ordinary raster images arrive through the
// same field. There is deliberately no glyph-icon mechanism — drawing an icon with an arbitrary
// Unicode character was only ever a stand-in for real image loading. Fetching and caching are the
// client's business; this model knows nothing about any image library or UI framework.
@Serializable
@SerialName("image")
@KompotComponentMarker
data class KompotImageComponent(
    override val id: String,
    val url: String,
    val contentDescription: String? = null,
    val scaleType: ImageScaleType = ImageScaleType.Fit,
    // Optional tint for monochrome SVG icons; null leaves the image as it is.
    val tint: ColorToken? = null,
    override val modifiers: List<KompotModifierNode> = emptyList(),
) : KompotComponent

@Serializable
enum class ImageScaleType {
    @SerialName("crop")
    Crop,

    @SerialName("fit")
    Fit,

    @SerialName("fill_bounds")
    FillBounds,
}
