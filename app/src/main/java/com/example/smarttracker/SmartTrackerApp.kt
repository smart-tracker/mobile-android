package com.example.smarttracker

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.util.DebugLogger
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer
import javax.inject.Inject

/**
 * Application класс. Аннотация @HiltAndroidApp запускает
 * кодогенерацию Hilt и инициализирует граф зависимостей.
 *
 * MapLibre инициализируется здесь один раз — до создания любого MapView.
 *
 * [Configuration.Provider] подключает [HiltWorkerFactory] к WorkManager,
 * чтобы воркеры могли получать зависимости через @AssistedInject.
 * При наличии этого интерфейса WorkManager НЕ инициализируется автоматически —
 * первый вызов WorkManager.getInstance(context) выполнит ленивую инициализацию
 * с нашей конфигурацией.
 *
 * [ImageLoaderFactory] настраивает Coil использовать тот же [OkHttpClient],
 * что и Retrofit — с auth-интерцептором (Bearer-токен). Это нужно для загрузки
 * фото профиля, которое может требовать авторизацию. [newImageLoader] вызывается
 * лениво при первом обращении к Coil — после завершения Hilt-инъекции в [onCreate].
 */
@HiltAndroidApp
class SmartTrackerApp : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var okHttpClient: OkHttpClient

    override fun onCreate() {
        super.onCreate()
        // null = без API-ключа; WellKnownTileServer.MapLibre = стандартный tile-сервер
        MapLibre.getInstance(this, null, WellKnownTileServer.MapLibre)
    }

    // Coil 2.x: ImageLoaderFactory.newImageLoader() — Application сам является Context
    // DebugLogger пишет в logcat тег "Coil" — помогает диагностировать ошибки загрузки.
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .logger(if (BuildConfig.DEBUG) DebugLogger() else null)
            .build()

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
