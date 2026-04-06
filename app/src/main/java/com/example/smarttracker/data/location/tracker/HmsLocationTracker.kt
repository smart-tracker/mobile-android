package com.example.smarttracker.data.location.tracker

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.example.smarttracker.data.location.model.TrackLocation
import com.example.smarttracker.data.location.model.TrackingConfig
import com.example.smarttracker.data.location.model.TrackingPriority
import com.example.smarttracker.data.location.model.toTrackLocation
import com.huawei.hms.location.LocationCallback
import com.huawei.hms.location.LocationRequest
import com.huawei.hms.location.LocationResult
import com.huawei.hms.location.LocationServices

/**
 * Реализация [LocationTracker] на базе Huawei Location Kit (HMS).
 *
 * Используется на Huawei-устройствах без Google Play Services (Mate 40+, P40+
 * с HarmonyOS). API почти идентичен GMS FLP, но с рядом отличий:
 * - [LocationRequest] создаётся через `.create()` + сеттеры (не Builder)
 * - Пакет: `com.huawei.hms.location.*`
 * - [LocationResult.locations] возвращает стандартный `android.location.Location`
 *   → тот же [toTrackLocation] работает без изменений
 *
 * Маппинг [TrackingPriority] → HMS константы:
 * - HIGH_ACCURACY → [LocationRequest.PRIORITY_HIGH_ACCURACY] (100)
 * - BALANCED      → [LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY] (102)
 * - LOW_POWER     → [LocationRequest.PRIORITY_LOW_POWER] (104)
 *
 * Все вызовы, требующие разрешений, обёрнуты в try-catch(SecurityException).
 */
class HmsLocationTracker(context: Context) : LocationTracker {

    private val client = LocationServices.getFusedLocationProviderClient(context)
    private var locationCallback: LocationCallback? = null

    @SuppressLint("MissingPermission")
    override fun startTracking(config: TrackingConfig, onLocation: (TrackLocation) -> Unit) {
        val hmsPriority = when (config.priority) {
            TrackingPriority.HIGH_ACCURACY -> LocationRequest.PRIORITY_HIGH_ACCURACY
            TrackingPriority.BALANCED      -> LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
            TrackingPriority.LOW_POWER     -> LocationRequest.PRIORITY_LOW_POWER
        }

        // HMS LocationRequest API: create() + сеттеры вместо Builder
        val request = LocationRequest.create().apply {
            interval             = config.intervalMs
            priority             = hmsPriority
            smallestDisplacement = config.minDistanceMeters
        }

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
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
