package io.github.youndie.kompot.images

import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.plus
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.ColorToken
import io.github.youndie.kompot.kompotCoreSerializersModule
import io.github.youndie.kompot.generated.generatedImagesSerializersModule
import kotlin.test.Test
import kotlin.test.assertEquals

private val json =
    Json {
        classDiscriminator = "type"
        serializersModule = kompotCoreSerializersModule + generatedImagesSerializersModule
    }

class SerializersTest {
    @Test
    fun `KompotImageComponent round-trips through the open KompotComponent type — including a null tint`() {
        val component: KompotComponent =
            KompotImageComponent(id = "logo", url = "https://example.com/logo.svg", contentDescription = "Logo")

        // A reified decodeFromString<T>()/encodeToString(component) does not resolve a serialiser for
        // a non-sealed interface on Kotlin/Native; an explicit PolymorphicSerializer(KompotComponent::class)
        // behaves the same way on every target.
        val componentSerializer = PolymorphicSerializer(KompotComponent::class)
        val decoded = json.decodeFromString(componentSerializer, json.encodeToString(componentSerializer, component))

        assertEquals(component, decoded)
    }

    @Test
    fun `every optional field round-trips when set`() {
        val component =
            KompotImageComponent(
                id = "icon",
                url = "https://example.com/ic_gift.svg",
                contentDescription = "Gift",
                scaleType = ImageScaleType.Crop,
                tint = ColorToken("primary"),
            )

        val decoded = json.decodeFromString<KompotImageComponent>(json.encodeToString(component))

        assertEquals(component, decoded)
    }

    @Test
    fun `defaults to Fit scale type and no tint`() {
        val component = KompotImageComponent(id = "banner", url = "https://example.com/banner.png")

        assertEquals(ImageScaleType.Fit, component.scaleType)
        assertEquals(null, component.tint)
    }
}
