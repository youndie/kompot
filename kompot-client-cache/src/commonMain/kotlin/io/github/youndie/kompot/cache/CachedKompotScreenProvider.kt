package io.github.youndie.kompot.cache

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.decodeKompotComponent
import io.github.youndie.kompot.encodeKompotComponent
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

// The offline-first loader of screens. On a cache hit it returns what it has IMMEDIATELY, without
// blocking the caller on the network, and revalidates in the background with an If-None-Match
// request: a 304 means the cache still holds, fresh data quietly replaces it, and the next visit to
// the screen sees the new version. On a cache miss it makes an unconditional request and caches the
// result.
@OptIn(ExperimentalTime::class)
public class CachedKompotScreenProvider(
    private val store: KompotScreenCacheStore,
    private val fetcher: KompotScreenFetcher,
    private val json: Json,
    // The scope for background revalidation comes from the caller: this class owns no long-lived
    // CoroutineScope of its own.
    private val scope: CoroutineScope,
) {
    public suspend fun getScreen(key: String): KompotComponent {
        val cached = store.get(key)
        if (cached != null) {
            scope.launch { revalidate(key, cached.etag) }
            return decode(cached.payload)
        }

        // An unconditional request (ifNoneMatch = null) — the server must answer Modified.
        val result = fetcher.fetch(key, ifNoneMatch = null) as KompotFetchResult.Modified
        store.put(CachedScreenEntry(key, encode(result.component), result.etag, now()))
        return result.component
    }

    // For the case where the CLIENT knows a screen is stale without waiting for the next visit.
    // Background revalidation updates the cache but hands the result to the NEXT getScreen, so after a
    // mutation the change would otherwise appear only on the second visit. A caller that just
    // submitted a form which changes what this screen shows drops the entry explicitly: the next
    // getScreen for it takes the cache-miss path — a blocking unconditional request, fresh data at
    // once rather than one visit later.
    public suspend fun invalidate(key: String) {
        store.clear(key)
    }

    private suspend fun revalidate(
        key: String,
        etag: String?,
    ) {
        when (val result = fetcher.fetch(key, ifNoneMatch = etag)) {
            is KompotFetchResult.Modified -> store.put(CachedScreenEntry(key, encode(result.component), result.etag, now()))
            KompotFetchResult.NotModified -> Unit
        }
    }

    private fun decode(payload: String): KompotComponent = json.decodeKompotComponent(payload)

    private fun encode(component: KompotComponent): String = json.encodeKompotComponent(component)

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()
}
