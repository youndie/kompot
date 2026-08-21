package io.github.youndie.kompot.interop

import io.github.youndie.kompot.KompotModifierNode
import io.github.youndie.kompot.images.KompotImageComponent
import io.github.youndie.kompot.standard.TextComponent

// ColorToken and TypographyToken are @JvmInline value classes, and they erode to an opaque `id` at
// the ObjC export boundary. This is not a guess: the generated header exports TextComponent.style as
// `id? style` rather than `TypographyToken?`, and a background node's colour as `id color`. Swift
// cannot reach `.key` on such a value.
//
// So it is unwrapped here, in Kotlin, where the value class is not erased yet — the erosion happens
// at the export boundary of a property, not inside an ordinary Kotlin function. The exported
// signature of the function itself returns a bare String, so the problem does not reappear on the
// way back.
//
// Only the toolkit's own types are here. An application's components have tokens too, and the same
// unwrapping belongs with them.
fun KompotModifierNode.Background.colorKey(): String = color.key

fun KompotModifierNode.Gradient.colorKeys(): List<String> = colors.map { it.key }

fun TextComponent.styleKey(): String? = style?.key

fun KompotImageComponent.tintKey(): String? = tint?.key
