package io.github.youndie.kompot.cache

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import io.github.youndie.kompot.KompotComponent
import io.github.youndie.kompot.KompotModifierNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Serializable
@SerialName("test_component")
private data class ProviderTestComponent(
    override val id: String,
    override val modifiers: List<KompotModifierNode> = emptyList(),
    val label: String = "",
) : KompotComponent

private val testJson =
    Json {
        classDiscriminator = "type"
        serializersModule =
            SerializersModule {
                polymorphic(KompotComponent::class) {
                    subclass(ProviderTestComponent::class, ProviderTestComponent.serializer())
                }
            }
    }

private class FakeCacheStore : KompotScreenCacheStore {
    val entries = mutableMapOf<String, CachedScreenEntry>()

    override suspend fun get(key: String): CachedScreenEntry? = entries[key]

    override suspend fun put(entry: CachedScreenEntry) {
        entries[entry.key] = entry
    }

    override suspend fun clear(key: String) {
        entries.remove(key)
    }
}

private class FakeFetcher(
    private val responses: MutableList<KompotFetchResult>,
) : KompotScreenFetcher {
    val calls = mutableListOf<Pair<String, String?>>()

    override suspend fun fetch(
        key: String,
        ifNoneMatch: String?,
    ): KompotFetchResult {
        calls += key to ifNoneMatch
        return responses.removeAt(0)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CachedKompotScreenProviderTest {
    @Test
    fun `a cache miss blocks on a fetch, then stores and returns the result`() =
        runTest {
            val store = FakeCacheStore()
            val fetcher =
                FakeFetcher(
                    mutableListOf(KompotFetchResult.Modified(ProviderTestComponent("c1", label = "hello"), etag = "\"v1\"")),
                )
            val provider = CachedKompotScreenProvider(store, fetcher, testJson, this)

            val result = provider.getScreen("home")

            assertEquals(ProviderTestComponent("c1", label = "hello"), result)
            assertEquals(listOf<Pair<String, String?>>("home" to null), fetcher.calls)
            assertEquals("\"v1\"", store.entries.getValue("home").etag)
        }

    @Test
    fun `a cache hit returns immediately without waiting for the background revalidation`() =
        runTest {
            val store = FakeCacheStore()
            store.entries["home"] =
                CachedScreenEntry(
                    key = "home",
                    payload = testJson.encodeToString(PolymorphicSerializer(KompotComponent::class), ProviderTestComponent("cached", label = "old")),
                    etag = "\"v1\"",
                    fetchedAt = 0L,
                )
            val fetcher = FakeFetcher(mutableListOf(KompotFetchResult.NotModified))
            val provider = CachedKompotScreenProvider(store, fetcher, testJson, this)

            val result = provider.getScreen("home")

            // Returned straight from the cache — background revalidation has not run yet.
            assertEquals(ProviderTestComponent("cached", label = "old"), result)
            assertEquals(emptyList(), fetcher.calls)

            advanceUntilIdle()

            assertEquals(listOf<Pair<String, String?>>("home" to "\"v1\""), fetcher.calls)
        }

    @Test
    fun `a NotModified background revalidation leaves the cached entry untouched`() =
        runTest {
            val store = FakeCacheStore()
            val originalEntry =
                CachedScreenEntry(
                    key = "home",
                    payload = testJson.encodeToString(PolymorphicSerializer(KompotComponent::class), ProviderTestComponent("cached")),
                    etag = "\"v1\"",
                    fetchedAt = 42L,
                )
            store.entries["home"] = originalEntry
            val fetcher = FakeFetcher(mutableListOf(KompotFetchResult.NotModified))
            val provider = CachedKompotScreenProvider(store, fetcher, testJson, this)

            provider.getScreen("home")
            advanceUntilIdle()

            assertEquals(originalEntry, store.entries["home"])
        }

    @Test
    fun `a Modified background revalidation overwrites the cached entry`() =
        runTest {
            val store = FakeCacheStore()
            store.entries["home"] =
                CachedScreenEntry(
                    key = "home",
                    payload = testJson.encodeToString(PolymorphicSerializer(KompotComponent::class), ProviderTestComponent("old", label = "stale")),
                    etag = "\"v1\"",
                    fetchedAt = 0L,
                )
            val fetcher =
                FakeFetcher(
                    mutableListOf(KompotFetchResult.Modified(ProviderTestComponent("new", label = "fresh"), etag = "\"v2\"")),
                )
            val provider = CachedKompotScreenProvider(store, fetcher, testJson, this)

            provider.getScreen("home")
            advanceUntilIdle()

            val updated = store.entries.getValue("home")
            assertEquals("\"v2\"", updated.etag)
            assertEquals(
                ProviderTestComponent("new", label = "fresh"),
                testJson.decodeFromString(PolymorphicSerializer(KompotComponent::class), updated.payload),
            )
        }

    @Test
    fun `clear removes the entry from the store`() =
        runTest {
            val store = FakeCacheStore()
            store.entries["home"] = CachedScreenEntry("home", "{}", null, 0L)

            store.clear("home")

            assertNull(store.entries["home"])
        }

    // A regression guard for a real bug: "the number on the main screen does not update even after
    // reopening it". On a cache hit getScreen returns the OLD value immediately and refreshes the
    // cache in the background for the NEXT visit, so after a known mutation the caller must
    // invalidate() the affected key — then the next getScreen takes the cache-miss path and gets fresh
    // data at once.
    @Test
    fun `invalidate clears the store entry so the next getScreen blocks on a fresh unconditional fetch`() =
        runTest {
            val store = FakeCacheStore()
            store.entries["home"] =
                CachedScreenEntry(
                    key = "home",
                    payload = testJson.encodeToString(PolymorphicSerializer(KompotComponent::class), ProviderTestComponent("stale", label = "old balance")),
                    etag = "\"v1\"",
                    fetchedAt = 0L,
                )
            val fetcher =
                FakeFetcher(
                    mutableListOf(KompotFetchResult.Modified(ProviderTestComponent("fresh", label = "new balance"), etag = "\"v2\"")),
                )
            val provider = CachedKompotScreenProvider(store, fetcher, testJson, this)

            provider.invalidate("home")
            val result = provider.getScreen("home")

            // An unconditional request (ifNoneMatch = null), not a conditional revalidation by the old
            // etag.
            assertEquals(listOf<Pair<String, String?>>("home" to null), fetcher.calls)
            assertEquals(ProviderTestComponent("fresh", label = "new balance"), result)
        }

    @Test
    fun `invalidate on an already-empty key is a harmless no-op`() =
        runTest {
            val store = FakeCacheStore()
            val fetcher = FakeFetcher(mutableListOf(KompotFetchResult.Modified(ProviderTestComponent("c1"), etag = "\"v1\"")))
            val provider = CachedKompotScreenProvider(store, fetcher, testJson, this)

            provider.invalidate("home")
            val result = provider.getScreen("home")

            assertEquals(ProviderTestComponent("c1"), result)
        }
}
