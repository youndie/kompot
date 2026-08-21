package io.github.youndie.kompot.interop

import io.github.youndie.kompot.KompotModifierNode
import io.github.youndie.kompot.ColorToken
import io.github.youndie.kompot.TypographyToken
import io.github.youndie.kompot.standard.TextComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KompotTokenBridgeTest {
    @Test
    fun `colorKey unwraps a Background node's ColorToken to a plain String`() {
        val node = KompotModifierNode.Background(ColorToken("primary"))

        assertEquals("primary", node.colorKey())
    }

    @Test
    fun `colorKeys unwraps every ColorToken in a Gradient node in order`() {
        val node = KompotModifierNode.Gradient(listOf(ColorToken("primary"), ColorToken("secondary")))

        assertEquals(listOf("primary", "secondary"), node.colorKeys())
    }

    @Test
    fun `styleKey unwraps a TextComponent's TypographyToken when present`() {
        val component = TextComponent(id = "t", text = "hello", style = TypographyToken("headline_large"))

        assertEquals("headline_large", component.styleKey())
    }

    @Test
    fun `styleKey is null when the component has no explicit style`() {
        val component = TextComponent(id = "t", text = "hello")

        assertNull(component.styleKey())
    }
}
