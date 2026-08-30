package io.github.youndie.kompot.cache

// The contract of the local screen cache. A concrete backend implements it; this module knows nothing
// about databases or files.
public interface KompotScreenCacheStore {
    public suspend fun get(key: String): CachedScreenEntry?

    public suspend fun put(entry: CachedScreenEntry)

    public suspend fun clear(key: String)
}
