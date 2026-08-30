package io.github.youndie.kompot.cache

// A raw cache entry: a KompotComponent already serialised to a string, not the component itself. The
// store deliberately knows nothing about polymorphic serialisation — that is the provider's business,
// since it is the one holding a Json.
public data class CachedScreenEntry(
    val key: String,
    val payload: String,
    val etag: String?,
    val fetchedAt: Long,
)
