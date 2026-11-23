package com.mochyy.mochymusic.shared.desktop

import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import com.mochyy.mochymusic.constants.MaxImageCacheSizeKey
import com.mochyy.mochymusic.shared.image.DefaultImageCacheMb
import com.mochyy.mochymusic.shared.image.mochyImageDefaults
import com.mochyy.mochymusic.utils.PreferencesCache
import java.io.File
import okio.Path.Companion.toPath

// Covers on desktop get the same crossfade and memory share as Android, plus a disk cache in the
// platform's cache folder, sized by the same setting the Android loader reads.
internal fun configureDesktopImageLoader() {
    val cacheSizeMb = (PreferencesCache.current(desktopPlaybackPreferences.store)[MaxImageCacheSizeKey]
        ?: DefaultImageCacheMb).toLong()
    SingletonImageLoader.setSafe { context ->
        ImageLoader.Builder(context)
            .mochyImageDefaults(context)
            .components {
                add(OkHttpNetworkFetcherFactory())
            }
            .apply {
                if (cacheSizeMb == 0L) {
                    diskCachePolicy(CachePolicy.DISABLED)
                } else {
                    val directory = desktopImageCacheDirectory()
                    if (directory != null) {
                        diskCache(
                            DiskCache.Builder()
                                .directory(directory.resolve("coil").absolutePath.toPath())
                                .maxSizeBytes(cacheSizeMb * 1024L * 1024L)
                                .build()
                        )
                    }
                }
            }
            .build()
    }
}

// the one image cache folder DesktopPaths names, so the storage page measures what coil writes; null
// when the folder can't be made, so a read-only home falls back to the memory cache instead of crashing
private fun desktopImageCacheDirectory(): File? =
    DesktopPaths.images.takeIf { it.isDirectory || it.mkdirs() }
