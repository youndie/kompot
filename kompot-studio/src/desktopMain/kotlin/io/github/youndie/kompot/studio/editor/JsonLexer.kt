package io.github.youndie.kompot.studio.editor

// ONE PASS, TWO ANSWERS: where every token is, and where every node is.
//
// A highlighter and a path→offset map are the same walk over the same text, and doing them twice is
// how they come to disagree — the caret lands on a node the colours say is somewhere else. The second
// answer is the one that is hard to get any other way: kotlinx.serialization parses to a tree with no
// offsets in it at all, so nothing that decodes a body can say where in the text a node was written.
//
// Written rather than taken from a library, and the reason is that second answer: Highlights (the KMP
// engine) colours JSON perfectly well and knows nothing about paths, so it would be a dependency plus
// a second pass to do the half it cannot.
internal enum class TokenKind {
    // A property name. Told apart from a string VALUE, because that difference is most of what makes
    // JSON readable at a glance.
    KEY,

    // The discriminator's key and its value, in a style of their own: `type` is what a reader looks
    // for first in a kompot body, and what every path in the tree is anchored on.
    TYPE_KEY,
    TYPE_VALUE,
    STRING,
    NUMBER,

    // true, false, null.
    LITERAL,
    PUNCTUATION,
}

internal data class JsonToken(
    val start: Int,
    val end: Int,
    val kind: TokenKind,
)

internal data class LexedJson(
    val tokens: List<JsonToken>,
    // The path notation the tree and the findings use, to the range worth putting a caret on: the
    // node's own `"type"` value where it has one, and its opening brace where it does not.
    val nodes: Map<String, IntRange>,
)

// Never throws and never gives up early on purpose. A body under the cursor is malformed most of the
// time — half a key, a missing comma — and an editor that stopped colouring at the first surprise
// would flicker the whole document grey on every keystroke. What it cannot read, it leaves unstyled.
internal fun lexJson(text: String): LexedJson = JsonScanner(text).scan()

// A class rather than nested functions, and not by preference: the object, array and value readers
// call each other, and a local function cannot name one declared after it.
private class JsonScanner(
    private val text: String,
) {
    private var at = 0
    private val tokens = mutableListOf<JsonToken>()
    private val nodes = mutableMapOf<String, IntRange>()

    fun scan(): LexedJson {
        value("$")
        return LexedJson(tokens, nodes)
    }

    private fun skipWhitespace() {
        while (at < text.length && text[at].isWhitespace()) at++
    }

    private fun punctuation() {
        tokens += JsonToken(at, at + 1, TokenKind.PUNCTUATION)
        at++
    }

    // The whole literal INCLUDING its quotes, or null when the string never closes — which is what
    // half a typed key looks like.
    private fun readString(): IntRange? {
        val start = at
        at++
        while (at < text.length) {
            when (text[at]) {
                '\\' -> at += 2
                '"' -> {
                    at++
                    return start until at
                }

                else -> at++
            }
        }
        return null
    }

    private fun readObject(path: String) {
        val brace = at
        punctuation()
        var typeValue: IntRange? = null

        while (true) {
            skipWhitespace()
            if (at >= text.length) break
            if (text[at] == '}') {
                punctuation()
                break
            }
            if (text[at] == ',') {
                punctuation()
                continue
            }
            if (text[at] != '"') break

            val key = readString() ?: break
            val name = text.substring(key.first + 1, key.last)
            val isType = name == DISCRIMINATOR
            tokens += JsonToken(key.first, key.last + 1, if (isType) TokenKind.TYPE_KEY else TokenKind.KEY)

            skipWhitespace()
            if (at < text.length && text[at] == ':') punctuation()
            skipWhitespace()

            if (isType && at < text.length && text[at] == '"') {
                val range = readString() ?: break
                tokens += JsonToken(range.first, range.last + 1, TokenKind.TYPE_VALUE)
                typeValue = range
            } else {
                value("$path.$name")
            }
        }

        // The type's value where there is one: a click in the tree should land on the word that names
        // the node, not on its punctuation.
        nodes[path] = typeValue ?: (brace..brace)
    }

    private fun readArray(path: String) {
        punctuation()
        var index = 0
        while (true) {
            skipWhitespace()
            if (at >= text.length) break
            if (text[at] == ']') {
                punctuation()
                break
            }
            if (text[at] == ',') {
                punctuation()
                continue
            }
            val before = at
            value("$path[$index]")
            index++
            // The one guard against a loop that cannot end: a character `value` refuses to consume
            // would otherwise be offered to it forever.
            if (at == before) break
        }
    }

    private fun value(path: String) {
        skipWhitespace()
        if (at >= text.length) return

        when {
            text[at] == '{' -> readObject(path)
            text[at] == '[' -> readArray(path)
            text[at] == '"' -> readString()?.let { tokens += JsonToken(it.first, it.last + 1, TokenKind.STRING) }
            text[at] == '-' || text[at].isDigit() -> {
                val start = at
                while (at < text.length && (text[at].isDigit() || text[at] in "-+.eE")) at++
                tokens += JsonToken(start, at, TokenKind.NUMBER)
            }

            text.startsWith("true", at) || text.startsWith("null", at) -> {
                tokens += JsonToken(at, at + 4, TokenKind.LITERAL)
                at += 4
            }

            text.startsWith("false", at) -> {
                tokens += JsonToken(at, at + 5, TokenKind.LITERAL)
                at += 5
            }

            else -> Unit
        }
    }
}

private const val DISCRIMINATOR = "type"
