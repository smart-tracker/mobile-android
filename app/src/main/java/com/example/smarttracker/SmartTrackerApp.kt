package com.example.smarttracker

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer

/**
 * Application класс. Аннотация @HiltAndroidApp запускает
 * кодогенерацию Hilt и инициализирует граф зависимостей.
 *
 * MapLibre инициализируется здесь один раз — до создания любого MapView.
 */
@HiltAndroidApp
class SmartTrackerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // null = без API-ключа; WellKnownTileServer.MapLibre = стандартный tile-сервер
        MapLibre.getInstance(this, null, WellKnownTileServer.MapLibre)
    }
}
