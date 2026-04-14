package com.adityaprasad.vaultdrop

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class VaultDropApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        try {
            com.yausername.youtubedl_android.YoutubeDL.getInstance().init(this)
            com.yausername.ffmpeg.FFmpeg.getInstance().init(this)
        } catch (e: Exception) {
            Log.e("VaultDropApp", "Failed to initialize YoutubeDL/FFmpeg: ${e.message}")
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .crossfade(false)
            .allowRgb565(true)
            .respectCacheHeaders(false)
            .memoryCache {
                coil.memory.MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_image_cache"))
                    .maxSizeBytes(80L * 1024L * 1024L)
                    .build()
            }
            .build()
    }
}
