package com.example

import android.app.Application
import android.graphics.Bitmap
import android.os.Looper
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.size.Precision
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class NafiTvApp : Application(), ImageLoaderFactory {

    private var customImageLoader: ImageLoader? = null

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 1. Register optimized Coil ImageLoader globally so all AsyncImage instances use low-RAM decode
        try {
            coil.Coil.setImageLoader(newImageLoader())
        } catch (e: Exception) {
            Log.w("NafiTvApp", "Coil image loader init error", e)
        }

        // 2. Global crash protection: Intercepts background decoder / OkHttp / coroutine crashes
        // preventing unexpected process termination on low-spec devices and TV boxes
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("NafiTvApp", "Intercepted crash on thread: ${thread.name}", throwable)
            if (thread != Looper.getMainLooper().thread) {
                // Background thread error (e.g. MediaCodec, DNS resolution, Coil decoding) -> ignore & recover
                Log.w("NafiTvApp", "Suppressed background thread exception: ${throwable.message}")
            } else {
                Log.w("NafiTvApp", "Suppressed main thread uncaught exception: ${throwable.message}")
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        return customImageLoader ?: synchronized(this) {
            customImageLoader ?: buildOptimizedImageLoader().also { customImageLoader = it }
        }
    }

    private fun buildOptimizedImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(sharedOkHttpClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.12) // Safe 12% memory limit prevents Low Memory Killer (LMK) on 1-2GB RAM phones
                    .strongReferencesEnabled(true)
                    .weakReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "nafitv_image_cache"))
                    .maxSizeBytes(35L * 1024 * 1024) // 35 MB disk cache limit
                    .build()
            }
            .bitmapConfig(Bitmap.Config.RGB_565) // 50% memory saving on all channel logos & posters
            .allowHardware(false) // Disables hardware bitmaps for 100% crash-free stability on TV boxes & low-RAM Mali/PowerVR GPUs
            .allowRgb565(true)
            .crossfade(false) // Saves GPU compositing passes on low-RAM devices
            .precision(Precision.INEXACT) // Automatically downsamples posters and logos to target UI size (huge memory savings!)
            .networkObserverEnabled(true)
            .respectCacheHeaders(false)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        try {
            customImageLoader?.memoryCache?.clear()
            if (level >= TRIM_MEMORY_MODERATE) {
                System.gc()
            }
        } catch (e: Exception) {
            Log.w("NafiTvApp", "Error trimming memory", e)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        try {
            customImageLoader?.memoryCache?.clear()
            System.gc()
        } catch (e: Exception) {
            Log.w("NafiTvApp", "Error on low memory", e)
        }
    }

    companion object {
        lateinit var instance: NafiTvApp
            private set

        val sharedOkHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .connectionPool(okhttp3.ConnectionPool(8, 2, TimeUnit.MINUTES))
                .retryOnConnectionFailure(true)
                .build()
        }
    }
}
