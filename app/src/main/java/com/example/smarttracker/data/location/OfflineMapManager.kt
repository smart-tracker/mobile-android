package com.example.smarttracker.data.location

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos

/**
 * Тихая предзагрузка офлайн-региона карты при первом GPS-fix с Wi-Fi.
 *
 * Офлайн-стратегия (три уровня):
 * 1. Авто-кэш MapLibre (до 100 МБ) — работает автоматически, код не нужен.
 * 2. Этот класс — скачивает bbox 5×5 км вокруг первого GPS-fix при наличии Wi-Fi.
 * 3. OfflineMapFallback — показывается только когда тайлы недоступны и кэш не покрывает.
 *
 * Лимит MapLibre Offline = 6000 тайлов. Zooms 12–16 в bbox 5×5 км ≈ 700 тайлов — укладывается.
 * FIFO: максимум [MAX_REGIONS] регионов, старший удаляется при превышении.
 */
@Singleton
class OfflineMapManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val offlineManager = OfflineManager.getInstance(context)

    /**
     * Флаг однократной загрузки за сессию тренировки.
     * Сбрасывается через [reset] при завершении тренировки.
     */
    private var downloadStarted = false

    /**
     * Вызывается при первом хорошем GPS-fix из LocationTrackingService.
     * Если Wi-Fi недоступен или загрузка уже начата — ничего не делает.
     *
     * @param center координаты первого GPS-fix
     * @param isWifiConnected true если устройство подключено к Wi-Fi
     */
    fun downloadRegionIfNeeded(center: LatLng, isWifiConnected: Boolean) {
        if (!isWifiConnected || downloadStarted) return
        downloadStarted = true

        offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                // FIFO: если регионов уже MAX_REGIONS — удалить самый старый
                if ((offlineRegions?.size ?: 0) >= MAX_REGIONS) {
                    offlineRegions?.firstOrNull()?.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                        override fun onDelete() {}
                        override fun onError(error: String) {}
                    })
                }

                val bbox = toBbox(center, radiusKm = 2.5)
                val definition = OfflineTilePyramidRegionDefinition(
                    STYLE_URL,
                    LatLngBounds.from(bbox.north, bbox.east, bbox.south, bbox.west),
                    /* minZoom = */ 12.0,
                    /* maxZoom = */ 16.0,
                    context.resources.displayMetrics.density,
                )

                offlineManager.createOfflineRegion(
                    definition,
                    byteArrayOf(),
                    object : OfflineManager.CreateOfflineRegionCallback {
                        override fun onCreate(offlineRegion: OfflineRegion) {
                            // Запустить загрузку тихо. Нет UI-индикации, ошибки не показываются.
                            offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                        }

                        override fun onError(error: String) {
                            // Тихая ошибка — офлайн-регион не критичен для работы приложения
                        }
                    },
                )
            }

            override fun onError(error: String) {
                // Тихая ошибка — сброс флага чтобы можно было попробовать снова при следующем старте
                downloadStarted = false
            }
        })
    }

    /**
     * Сбросить флаг при завершении тренировки.
     * Вызывается из WorkoutStartViewModel.onFinishClick().
     */
    fun reset() {
        downloadStarted = false
    }

    companion object {
        /** Максимальное число сохранённых офлайн-регионов; старший удаляется по FIFO */
        const val MAX_REGIONS = 3
        const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

        private data class BboxDegrees(
            val north: Double, val south: Double,
            val east: Double,  val west: Double,
        )

        /**
         * Преобразует центр и радиус в bbox в градусах.
         * 1° широты ≈ 111 км; 1° долготы ≈ 111 * cos(lat) км.
         */
        private fun toBbox(center: LatLng, radiusKm: Double): BboxDegrees {
            val dLat = radiusKm / 111.0
            val dLng = radiusKm / (111.0 * cos(Math.toRadians(center.latitude)))
            return BboxDegrees(
                north = center.latitude  + dLat,
                south = center.latitude  - dLat,
                east  = center.longitude + dLng,
                west  = center.longitude - dLng,
            )
        }
    }
}
