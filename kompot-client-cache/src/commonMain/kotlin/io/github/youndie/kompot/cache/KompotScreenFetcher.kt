package io.github.youndie.kompot.cache

import io.github.youndie.kompot.KompotComponent

// The result of fetching a screen with an optional conditional request: the server either sent fresh
// data (Modified, with a new ETag) or confirmed the cached version still holds (NotModified, a 304
// with no body at all).
sealed class KompotFetchResult {
    data class Modified(
        val component: KompotComponent,
        val etag: String?,
    ) : KompotFetchResult()

    data object NotModified : KompotFetchResult()
}

// The fetching contract. An application plugs in a concrete HTTP implementation; this module knows
// nothing about the network. ifNoneMatch == null means an unconditional request, and a server
// answering NotModified to one of those would be misbehaving.
fun interface KompotScreenFetcher {
    suspend fun fetch(
        key: String,
        ifNoneMatch: String?,
    ): KompotFetchResult
}
