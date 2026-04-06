package com.example.smarttracker.data.location.tracker

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.example.smarttracker.data.location.model.TrackLocation
import com.example.smarttracker.data.location.model.TrackingConfig
import com.example.smarttracker.data.location.model.TrackingPriority
import com.example.smarttracker.data.location.model.toTrackLocation
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Реализация [LocationTracker] на базе Google FusedLocationProviderClient (GMS).
 *
 * FLP автоматически выбирает лучший источник (GPS + сеть + датчики) и
 * применяет аппаратное сглаживание. Работает на всех устройствах с Google Play Services.
 *
 * Маппинг [TrackingPriority] → GMS [Priority]:
 * - HIGH_ACCURACY → PRIORITY_HIGH_ACCURACY (100)
 * - BALANCED      → PRIORITY_BALANCED_POWER_ACCURACY (102)
 * - LOW_POWER     → PRIORITY_LOW_POWER (104)
 *
 * Все вызовы, требующие разрешений, обёрнуты в try-catch(SecurityException).
 */
class GmsLocationTracker(context: Context) : LocationTracker {

    private val client = LocationServices.getFusedLocationProviderClient(context)
    private var locationCallback: LocationCallback? = null

    @SuppressLint("MissingPermission")
    override fun startTracking(config: TrackingConfig, onLocation: (TrackLocation) -> Unit) {
        val gmsPriority = when (config.priority) {
            TrackingPriority.HIGH_ACCURACY -> Priority.PRIORITY_HIGH_ACCURACY
            TrackingPriority.BALANCED      -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
            TrackingPriority.LOW_POWER     -> Priority.PRIORITY_LOW_POWER
        }

        val request = LocationRequest.Builder(gmsPriority, config.intervalMs)
            .setMinUpdateDistanceMeters(config.minDistanceMeters)
            // Ждать точного фикса перед первой доставкой — уменьшает прыжки в начале трека
            .setWaitForAccurateLocation(true)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                // result.locations может содержать несколько накопленных точек
                result.locations.forEach { loc ->
                    onLocation(loc.toTrackLocation())
                }
            }
        }
        locationCallback = callback

        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            // Разрешение отозвано после старта — ViewModel обнаружит через GPS-таймаут
        }
    }

    override fun stopTracking() {
        locationCallback?.let { client.removeLocationUpdates(it) }
        locationCallback = null
    }

    @SuppressLint("MissingPermission")
    override fun getLastKnownLocation(callback: (TrackLocation?) -> Unit) {
        try {
            client.lastLocation
                .addOnSuccessListener { location ->
                    if (location == null) {
                        callback(null)
                        return@addOnSuccessListener
                    }
                    // Проверяем свежесть: устаревшая позиция хуже, чем отсутствие позиции.
                    // Старый кэш (> LAST_LOCATION_MAX_AGE_MS) вводит в заблуждение — камера
                    // прыгает в другой город при первом запуске после долгого перерыва.
                    val ageMs = System.currentTimeMillis() - location.time
                    if (ageMs > com.example.smarttracker.data.location.LocationConfig.LAST_LOCATION_MAX_AGE_MS) {
                        callback(null)
                    } else {
                        callback(location.toTrackLocation())
                    }
                }
                .addOnFailureListener {
                    callback(null)
                }
        } catch (e: SecurityException) {
            callback(null)
        }
    }
}
