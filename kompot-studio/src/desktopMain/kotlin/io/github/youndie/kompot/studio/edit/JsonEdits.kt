package io.github.youndie.kompot.studio.edit

import io.github.youndie.kompot.studio.editor.LexedJson
import io.github.youndie.kompot.studio.editor.lexJson

// HALF THE EDITS TO A SCREEN ARE STRUCTURAL: reorder the cards, drop the banner, duplicate a row. In
// the text that is cutting a block out with its brackets balanced; in the tree it is one action.
//
// OVER THE TEXT, and not over a decoded tree — which is the same argument the whole studio is built
// on, met once more. Decoding and re-encoding loses an unfamiliar type (UnknownComponent does not
// encode as what arrived) and every property this build's classes do not declare
// (`ignoreUnknownKeys = true` drops them silently). A recording edited that way comes back smaller
// than it went in, and nothing says so.
//
// SURGICAL, and not "re-serialise with prettyPrint", which is what the plan reached for first: a
// fixture is committed in somebody's formatting, and reformatting the whole document to move one node
// turns a one-line change into a whole-file diff. The lexer already knows where every value begins and
// ends, so an edit is a splice.
internal object JsonEdits {
    // Two siblings swap their characters and nothing else moves — not the commas, not the indentation,
    // not the lines around them. Each element keeps the position it was written at, which is what makes
    // the diff readable.
    fun moveUp(
        text: String,
        path: String,
    ): String? = swap(text, path, offset = -1)

    fun moveDown(
        text: String,
        path: String,
    ): String? = swap(text, path, offset = +1)

    // The copy lands after the original, with an id of its own: a duplicate that kept the id would be
    // caught by the diagnostics panel a keystroke later (component-id), and a tool that creates its own
    // findings teaches people to ignore them.
    fun duplicate(
        text: String,
        path: String,
    ): String? {
        val lexed = lexJson(text)
        val span = lexed.spans[path] ?: return null
        val index = indexOf(path) ?: return null

        val original = text.substring(span.first, span.last + 1)
        val copy =
            lexed.ids[path]?.let { id ->
                // The id's range is absolute, so it is shifted into the copy's own coordinates before
                // the value inside its quotes is rewritten.
                val at = id.first - span.first
                val length = id.last - id.first + 1
                val renamed = original.substring(at, at + length).dropLast(1) + COPY_SUFFIX + "\""
                original.take(at) + renamed + original.drop(at + length)
            } ?: original

        // The document's own separator, taken from between two elements it already has, so a file
        // written one-per-line stays one-per-line and a compact one stays compact.
        val separator =
            lexed.spans[sibling(path, index - 1)]?.let { text.substring(it.last + 1, span.first) }
                ?: lexed.spans[sibling(path, index + 1)]?.let { text.substring(span.last + 1, it.first) }
                ?: ", "

        return text.take(span.last + 1) + separator + copy + text.drop(span.last + 1)
    }

    // Removed with the separator that attached it to its neighbours, or the array is left holding a
    // comma with nothing after it.
    fun delete(
        text: String,
        path: String,
    ): String? {
        val lexed = lexJson(text)
        val span = lexed.spans[path] ?: return null
        val index = indexOf(path) ?: return null

        val previous = lexed.spans[sibling(path, index - 1)]
        val next = lexed.spans[sibling(path, index + 1)]

        val from = previous?.let { it.last + 1 } ?: span.first
        val to = if (previous != null) span.last + 1 else next?.first ?: (span.last + 1)

        return text.take(from) + text.drop(to)
    }

    private fun swap(
        text: String,
        path: String,
        offset: Int,
    ): String? {
        val lexed = lexJson(text)
        val index = indexOf(path) ?: return null
        val here = lexed.spans[path] ?: return null
        val there = lexed.spans[sibling(path, index + offset)] ?: return null

        val (first, second) = if (here.first < there.first) here to there else there to here

        return buildString {
            append(text, 0, first.first)
            append(text, second.first, second.last + 1)
            append(text, first.last + 1, second.first)
            append(text, first.first, first.last + 1)
            append(text, second.last + 1, text.length)
        }
    }

    // Only an element of an array can be reordered, duplicated or removed: a property is not one of a
    // list, and moving it would mean rewriting its name.
    private fun indexOf(path: String): Int? {
        if (!path.endsWith("]")) return null
        return path.substringAfterLast('[').dropLast(1).toIntOrNull()
    }

    private fun sibling(
        path: String,
        index: Int,
    ): String = if (index < 0) "" else path.substringBeforeLast('[') + "[$index]"

    private const val COPY_SUFFIX = "-copy"
}

// Small bodies and small stacks: a screen is kilobytes, so remembering whole texts is cheaper than
// remembering operations AND their inverses — and an operation stack has to be right in both
// directions, which is twice the code and twice the ways to be wrong.
internal class EditHistory(
    initial: String,
) {
    private val past = ArrayDeque<String>()
    private val future = ArrayDeque<String>()
    private var current = initial

    val canUndo: Boolean get() = past.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()

    fun record(text: String) {
        if (text == current) return
        past.addLast(current)
        // A new edit ends the redo line, the way it does everywhere: the future it would return to is
        // no longer the future this text has.
        future.clear()
        current = text
        while (past.size > LIMIT) past.removeFirst()
    }

    fun undo(): String? {
        val previous = past.removeLastOrNull() ?: return null
        future.addLast(current)
        current = previous
        return previous
    }

    fun redo(): String? {
        val next = future.removeLastOrNull() ?: return null
        past.addLast(current)
        current = next
        return next
    }

    private companion object {
        const val LIMIT = 200
    }
}

internal fun LexedJson.hasSpan(path: String): Boolean = path in spans
