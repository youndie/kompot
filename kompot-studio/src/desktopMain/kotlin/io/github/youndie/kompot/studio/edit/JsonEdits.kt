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

    // ONE PROPERTY, WRITTEN IN PLACE. The same splice as the structural edits and for the same
    // reason: a fixture is committed in somebody's formatting, and rewriting the document to change a
    // spacing would turn a one-word edit into a whole-file diff.
    //
    // A property that is not there yet is inserted after the node's `type`, which every component
    // carries and which is therefore the one anchor that always exists — appending before the closing
    // brace would have to find it through whatever nesting the last value has.
    fun setProperty(
        text: String,
        path: String,
        name: String,
        value: String,
    ): String? {
        val lexed = lexJson(text)
        val existing = lexed.spans["$path.$name"]
        if (existing != null) return text.take(existing.first) + value + text.drop(existing.last + 1)

        val node = lexed.nodes[path] ?: return null
        // `nodes` points at the type's VALUE, so the insertion goes just after it.
        return text.take(node.last + 1) + ", \"$name\": " + value + text.drop(node.last + 1)
    }

    // Removing a property, which is how an optional one goes back to its default. Written beside the
    // setter because "clear this field" and "type into this field" are one control in a panel.
    fun removeProperty(
        text: String,
        path: String,
        name: String,
    ): String? {
        val lexed = lexJson(text)
        val value = lexed.spans["$path.$name"] ?: return null
        val key = text.lastIndexOf("\"$name\"", value.first)
        if (key < 0) return null

        // Back to the separator that introduced the key, and forward over the one that follows it if
        // this was the first property — either way exactly one comma leaves with it.
        val before = text.take(key).trimEnd()
        return if (before.endsWith(",")) {
            before.dropLast(1) + text.drop(value.last + 1)
        } else {
            val after = text.drop(value.last + 1)
            before + after.trimStart().removePrefix(",")
        }
    }

    // ADDING A NODE TO A SLOT, which is what a palette does and what a drag ends in.
    //
    // The slot is named rather than guessed: which properties hold components is the schema's answer
    // (childSlots), and a splice that looked for "the first array" would put a text node among a
    // table's rows.
    fun insertInto(
        text: String,
        parentPath: String,
        slot: String,
        index: Int,
        node: String,
        many: Boolean,
    ): String? {
        // A slot with room for one is written, not appended to. Getting this from the schema rather
        // than from the shape already in the document matters on the empty case: an absent
        // `emptyState` and an absent `children` look identical in the text, and only one of them
        // should become a list.
        if (!many) return setProperty(text, parentPath, slot, node)

        val lexed = lexJson(text)
        val slotSpan = lexed.spans["$parentPath.$slot"]

        // A slot that is not there yet — an empty `children` a server omitted — is written as a list
        // of one rather than refused: the alternative is a palette that works only on containers
        // somebody has already filled.
        if (slotSpan == null) return setProperty(text, parentPath, slot, "[$node]")

        val existing = childSpans(lexed, parentPath, slot)
        if (existing.isEmpty()) {
            // An empty array: replace it whole, which is the only case with no separator to copy.
            return text.take(slotSpan.first) + "[$node]" + text.drop(slotSpan.last + 1)
        }

        val at = index.coerceIn(0, existing.size)
        return if (at == existing.size) {
            val last = existing.last()
            val separator = separatorBetween(text, existing) ?: ", "
            text.take(last.last + 1) + separator + node + text.drop(last.last + 1)
        } else {
            val target = existing[at]
            val separator = separatorBetween(text, existing) ?: ", "
            text.take(target.first) + node + separator + text.drop(target.first)
        }
    }

    // MOVING ONE, which is a removal and an insertion and has to be in that order: the second reads
    // offsets, and offsets taken before the first are wrong by the length of what left.
    fun moveInto(
        text: String,
        from: String,
        parentPath: String,
        slot: String,
        index: Int,
        many: Boolean,
    ): String? {
        val span = lexJson(text).spans[from] ?: return null
        val node = text.substring(span.first, span.last + 1)

        // Refused rather than attempted: a node cannot be dropped inside itself, and the result would
        // be a document that parses and describes an infinite screen.
        if (parentPath == from || parentPath.startsWith("$from.") || parentPath.startsWith("$from[")) return null

        val without = delete(text, from) ?: return null

        // TAKING A NODE OUT RENUMBERS ITS LATER SIBLINGS, and the destination was named before it
        // left. Dropping the first child of a column onto the second means inserting into a node whose
        // path is now `[0]` — inserting into `[1]` puts it somewhere else, or nowhere, and the caller
        // has no way to know that because it named a target that was correct when it named it.
        val marker = from.substringBeforeLast('[', "")
        val removed = from.substringAfterLast('[', "").removeSuffix("]").toIntOrNull()
        val shifted = if (removed == null) parentPath else shiftAfterRemoval(parentPath, marker, removed)
        val at =
            if (removed != null && marker == "$parentPath.$slot" && index > removed) index - 1 else index

        return insertInto(without, shifted, slot, at, node, many)
    }

    private fun shiftAfterRemoval(
        path: String,
        marker: String,
        removed: Int,
    ): String {
        if (!path.startsWith("$marker[")) return path
        val rest = path.removePrefix("$marker[")
        val position = rest.substringBefore(']').toIntOrNull() ?: return path
        return if (position <= removed) path else "$marker[${position - 1}]" + rest.substringAfter(']')
    }

    private fun childSpans(
        lexed: LexedJson,
        parentPath: String,
        slot: String,
    ): List<IntRange> =
        generateSequence(0) { it + 1 }
            .map { lexed.spans["$parentPath.$slot[$it]"] }
            .takeWhile { it != null }
            .filterNotNull()
            .toList()

    // The document's own separator, read from between two elements it already has, so a file written
    // one-per-line stays one-per-line.
    private fun separatorBetween(
        text: String,
        spans: List<IntRange>,
    ): String? {
        if (spans.size < 2) return null
        return text.substring(spans[0].last + 1, spans[1].first)
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
