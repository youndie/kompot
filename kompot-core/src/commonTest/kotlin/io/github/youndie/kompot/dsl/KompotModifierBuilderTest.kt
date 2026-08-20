package io.github.youndie.kompot.dsl

import io.github.youndie.kompot.KompotModifierNode
import io.github.youndie.kompot.ColorToken
import io.github.youndie.kompot.SizeType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KompotModifierBuilderTest {
    @Test
    fun `defaults to an empty node list with nothing set`() {
        val modifiers = KompotModifierBuilder().build()

        assertTrue(modifiers.isEmpty())
    }

    @Test
    fun `padding defaults every side to zero except the ones passed`() {
        val modifiers = KompotModifierBuilder().apply { padding(top = 16, start = 8) }.build()

        assertEquals(listOf(KompotModifierNode.Padding(top = 16, bottom = 0, start = 8, end = 0)), modifiers)
    }

    @Test
    fun `background produces a single Background node`() {
        val modifiers = KompotModifierBuilder().apply { background(ColorToken("primary")) }.build()

        assertEquals(listOf(KompotModifierNode.Background(ColorToken("primary"))), modifiers)
    }

    @Test
    fun `gradientBackground produces a single Gradient node — independent of background`() {
        val tokens = listOf(ColorToken("primary"), ColorToken("secondary"))
        val modifiers = KompotModifierBuilder().apply { gradientBackground(tokens) }.build()

        assertEquals(listOf(KompotModifierNode.Gradient(tokens)), modifiers)
    }

    @Test
    fun `fillMaxWidth and fillMaxHeight switch size to Fill independently`() {
        val widthOnly = KompotModifierBuilder().apply { fillMaxWidth() }.build()
        val heightOnly = KompotModifierBuilder().apply { fillMaxHeight() }.build()
        val both = KompotModifierBuilder().apply { fillMaxWidth(); fillMaxHeight() }.build()

        assertEquals(listOf(KompotModifierNode.Size(width = SizeType.Fill, height = SizeType.Wrap)), widthOnly)
        assertEquals(listOf(KompotModifierNode.Size(width = SizeType.Wrap, height = SizeType.Fill)), heightOnly)
        assertEquals(listOf(KompotModifierNode.Size(width = SizeType.Fill, height = SizeType.Fill)), both)
    }

    // fillMaxWidth() and fillMaxHeight() describe ONE node with two dimensions. Were each call to
    // add its own node, the second would overwrite the first's dimension when the chain is folded.
    // This checks that the builder collapses both calls into a single node.
    @Test
    fun `a later fillMaxHeight call updates the existing Size node instead of appending a second one`() {
        val modifiers =
            KompotModifierBuilder()
                .apply {
                    fillMaxWidth()
                    background(ColorToken("surface")) // something between the two size calls
                    fillMaxHeight()
                }.build()

        assertEquals(
            listOf(
                KompotModifierNode.Size(width = SizeType.Fill, height = SizeType.Fill),
                KompotModifierNode.Background(ColorToken("surface")),
            ),
            modifiers,
        )
    }

    @Test
    fun `weight produces a single Weight node`() {
        val modifiers = KompotModifierBuilder().apply { weight(0.5f) }.build()

        assertEquals(listOf(KompotModifierNode.Weight(0.5f)), modifiers)
    }

    // Node order matters: the fold applies them in call order rather than in a hardcoded
    // padding/background/size order, as an earlier version did.
    @Test
    fun `build preserves call order across different node types`() {
        val modifiers =
            KompotModifierBuilder()
                .apply {
                    padding(top = 4, bottom = 4, start = 4, end = 4)
                    background(ColorToken("surface"))
                    fillMaxWidth()
                    weight(1f)
                }.build()

        assertEquals(
            listOf(
                KompotModifierNode.Padding(top = 4, bottom = 4, start = 4, end = 4),
                KompotModifierNode.Background(ColorToken("surface")),
                KompotModifierNode.Size(width = SizeType.Fill, height = SizeType.Wrap),
                KompotModifierNode.Weight(1f),
            ),
            modifiers,
        )
    }
}
