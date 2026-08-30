package io.github.youndie.kompot.tck

// One event of a text/event-stream recording. `data` is the concatenation SSE prescribes: several
// data: lines in one event join with a newline between them and mean ONE value, which is how a
// payload containing a line break travels legally.
public data class TckEvent(
    val name: String?,
    val data: String?,
    val malformed: List<String> = emptyList(),
)

// A reader for a recorded stream rather than a live one. The frames are what the protocol describes
// (SPEC.md §16.6); the connection is not, and a recording is enough to check everything except who
// receives which topic.
//
// Deliberately strict about lines it does not recognise: the point of the check is to catch a server
// that writes almost-SSE, and a lenient reader would quietly repair exactly the mistakes worth
// reporting.
public object TckEventStream {
    public fun parse(recording: String): List<TckEvent> {
        val events = mutableListOf<TckEvent>()
        var name: String? = null
        val data = mutableListOf<String>()
        val malformed = mutableListOf<String>()

        fun flush() {
            if (name == null && data.isEmpty() && malformed.isEmpty()) return
            events += TckEvent(name, data.takeIf { it.isNotEmpty() }?.joinToString("\n"), malformed.toList())
            name = null
            data.clear()
            malformed.clear()
        }

        recording.lineSequence().forEach { rawLine ->
            val line = rawLine.removeSuffix("\r")
            when {
                line.isEmpty() -> flush()
                // A comment, and the usual way a server keeps a connection open. Carries no meaning.
                line.startsWith(":") -> Unit
                line.startsWith("data:") -> data += line.removePrefix("data:").removePrefix(" ")
                line.startsWith("event:") -> name = line.removePrefix("event:").removePrefix(" ")
                line.startsWith("id:") || line.startsWith("retry:") -> Unit
                else -> malformed += line
            }
        }
        flush()

        return events
    }
}
