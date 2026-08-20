package io.github.youndie.kompot.dsl

import io.github.youndie.kompot.KompotModifierNode
import io.github.youndie.kompot.ColorToken
import io.github.youndie.kompot.SizeType

@KompotDsl
class KompotModifierBuilder {
    private val nodes = mutableListOf<KompotModifierNode>()

    fun padding(
        top: Int = 0,
        bottom: Int = 0,
        start: Int = 0,
        end: Int = 0,
    ) {
        nodes += KompotModifierNode.Padding(top = top, bottom = bottom, start = start, end = end)
    }

    fun background(token: ColorToken) {
        nodes += KompotModifierNode.Background(token)
    }

    fun gradientBackground(tokens: List<ColorToken>) {
        nodes += KompotModifierNode.Gradient(tokens)
    }

    fun fillMaxWidth() {
        updateOrAddSizeNode { it.copy(width = SizeType.Fill) }
    }

    fun fillMaxHeight() {
        updateOrAddSizeNode { it.copy(height = SizeType.Fill) }
    }

    fun weight(value: Float) {
        nodes += KompotModifierNode.Weight(value)
    }

    private fun updateOrAddSizeNode(update: (KompotModifierNode.Size) -> KompotModifierNode.Size) {
        val existingIndex = nodes.indexOfFirst { it is KompotModifierNode.Size }

        if (existingIndex != -1) {
            val existingNode = nodes[existingIndex] as KompotModifierNode.Size
            nodes[existingIndex] = update(existingNode)
        } else {
            val newNode = KompotModifierNode.Size(width = SizeType.Wrap, height = SizeType.Wrap)
            nodes += update(newNode)
        }
    }

    fun build(): List<KompotModifierNode> = nodes.toList()
}
