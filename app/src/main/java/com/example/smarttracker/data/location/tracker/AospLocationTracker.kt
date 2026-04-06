package com.example.smarttracker.data.location.tracker

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import com.example.smarttracker.data.location.model.TrackLocation
import com.example.smarttracker.data.location.model.TrackingConfig
import com.example.smarttracker.data.location.model.toTrackLocation

/**
 * Реализация [LocationTracker] на базе стандартного [LocationManager] (AOSP).
 *
 * Используется как fallback когда GMS и HMS недоступны.
 * Подписывается на оба провайдера ([LocationManager.GPS_PROVIDER] и
 * [LocationManager.NETWORK_PROVIDER]) — это повышает надёжность в помещениях,
 * где GPS слабый, а сеть доступна.
 *
 * Фильтрация по accuracy и запись в Room происходят на уровне
 * [LocationTrackingService] — здесь только сырые данные из ОС.
 *
 * Все вызовы, требующие разрешений, обёрнуты в try-catch(SecurityException):
 * разрешения запрашиваются до старта трекинга в LocationPermissionHandler.
 */
class AospLocationTracker(context: Context) : LocationTracker {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    // Один и тот же listener регистрируется на оба провайдера.
    // Дублей не будет — ОС возвращает лучший фикс из доступных источников.
    private var locationListener: LocationListener? = null

    @SuppressLint("MissingPermission")
    override fun startTracking(config: TrackingConfig, onLocation: (TrackLocation) -> Unit) {
        val listener = LocationListener { location ->
            onLocation(location.toTrackLocation())
        }
        locationListener = listener

        // GPS_PROVIDER — точный фикс на улице
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            try {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    config.intervalMs,
                    config.minDistanceMeters,
                    listener,
                    Looper.getMainLooper(),
                )
            } catch (e: SecurityException) {
                // Разрешение отозвано после запуска — молча игнорируем,
                // ViewModel обнаружит потерю GPS через 30-секундный таймаут.
            }
        }

        // NETWORK_PROVIDER — приблизительный фикс в помещении через Wi-Fi / сотовую сеть
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            try {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    config.intervalMs,
                    config.minDistanceMeters,
                    listener,
                    Looper.getMainLooper(),
                )
            } catch (e: SecurityException) {
                // Аналогично — не критично если GPS уже подписан
            }
        }
    }

    override fun stopTracking() {
        // removeUpdates безопасен при null listener начиная с API 26
        locationListener?.let {
            try {
                locationManager.removeUpdates(it)
            } catch (e: SecurityException) {
                // Игнорируем — listener всё равно перестанет получать обновления
            }
        }
        locationListener = null
    }

    @SuppressLint("MissingPermission")
    override fun getLastKnownLocation(callback: (TrackLocation?) -> Unit) {
        try {
            val gpsLoc     = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val networkLoc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            // Берём наиболее свежую из доступных позиций
            val best = listOfNotNull(gpsLoc, networkLoc).maxByOrNull { it.time }
            if (best == null) {
                callback(null)
                return
            }
            // Проверяем свежесть: позиция старше LAST_LOCATION_MAX_AGE_MS не несёт
            // актуальной информации и может ввести в заблуждение (старый кэш).
            val ageMs = System.currentTimeMillis() - best.time
            if (ageMs > com.example.smarttracker.data.location.LocationConfig.LAST_LOCATION_MAX_AGE_MS) {
                callback(null)
            } else {
                callback(best.toTrackLocation())
            }
        } catch (e: SecurityException) {
            callback(null)
        }
    }
}
