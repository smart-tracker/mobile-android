package com.example.smarttracker

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
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
 */
@HiltAndroidApp
class SmartTrackerApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        // null = без API-ключа; WellKnownTileServer.MapLibre = стандартный tile-сервер
        MapLibre.getInstance(this, null, WellKnownTileServer.MapLibre)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
