package io.github.youndie.kompot.registry

// WHAT A TYPE MEANS ON THE WIRE, carried from the source to the schema.
//
// A SerialDescriptor has no comments in it, so a generated schema can only describe SHAPE — that a
// property is an integer, that it may be null. What it means is prose, and prose that lives anywhere
// but beside the type goes stale on the first rename.
//
// KDOC, AND DELIBERATELY NOT THE `//` COMMENTS THIS TOOLKIT IS FULL OF. Those explain the code: why a
// property exists, what broke before it did, which order a resolution happens in. Useful, and not what
// a reader of the wire on another stack needs — copying them into a schema would make it longer and
// harder to read at once. So KDoc is the marked channel: writing one says "this sentence is for
// whoever reads the schema", and everything else stays a comment.
//
// It follows that this is opt-in and starts nearly empty. A type without KDoc prints exactly the
// schema it printed before.
public data class KompotComponentDoc(
    // The type itself: one sentence about what the node IS.
    val summary: String? = null,
    // Property name to its sentence. Only the ones somebody wrote.
    val properties: Map<String, String> = emptyMap(),
)
