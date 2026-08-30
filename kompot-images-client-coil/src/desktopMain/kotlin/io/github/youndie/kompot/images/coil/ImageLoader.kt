package io.github.youndie.kompot.images.coil

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import okio.Path.Companion.toPath

// crossfade(false) on purpose: a fade on every recomposition of an already cached image reads as
// flicker. All three cache policies are enabled because these images are static and versioned by
// file name on the server, so there is nothing to invalidate.
public fun createKompotImageLoader(diskCachePath: String): ImageLoader =
    ImageLoader
        .Builder(PlatformContext.INSTANCE)
        .components {
            add(KtorNetworkFetcherFactory())
            add(SvgDecoder.Factory())
        }.memoryCache {
            MemoryCache
                .Builder()
                .maxSizePercent(PlatformContext.INSTANCE, 0.15)
                .build()
        }.diskCache {
            DiskCache
                .Builder()
                .directory(diskCachePath.toPath())
                .maxSizeBytes(50L * 1024 * 1024)
                .build()
        }.crossfade(false)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .networkCachePolicy(CachePolicy.ENABLED)
        .build()
