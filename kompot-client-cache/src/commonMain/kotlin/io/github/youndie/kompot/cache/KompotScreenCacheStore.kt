package io.github.youndie.kompot.cache

// The contract of the local screen cache. A concrete backend implements it; this module knows nothing
// about databases or files.
interface KompotScreenCacheStore {
    suspend fun get(key: String): CachedScreenEntry?

    suspend fun put(entry: CachedScreenEntry)

    suspend fun clear(key: String)
}
