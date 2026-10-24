package com.texasprogram.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

/// Один загрузчик фото на всё приложение: плавное проявление, кэш в памяти
/// и на диске, экономный формат пикселей — меньше памяти, меньше пауз сборщика
/// мусора при прокрутке ленты.
class StrainApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .crossfade(160)
        // Фото — JPEG без прозрачности: 2 байта на пиксель вместо 4.
        .allowRgb565(true)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.2).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("images"))
                .maxSizeBytes(200L * 1024 * 1024)
                .build()
        }
        // Адрес фото никогда не меняется: снимок с диска не перепроверяем у сервера.
        .respectCacheHeaders(false)
        .build()
}
