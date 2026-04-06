package com.example.smarttracker.data.location

import android.content.Context
import com.example.smarttracker.data.location.model.LocationRuntime
import com.example.smarttracker.data.location.tracker.AospLocationTracker
import com.example.smarttracker.data.location.tracker.GmsLocationTracker
import com.example.smarttracker.data.location.tracker.HmsLocationTracker
import com.example.smarttracker.data.location.tracker.LocationTracker

/**
 * Фабрика трекеров геолокации.
 *
 * Определяет среду выполнения через [RuntimeDetector] и возвращает
 * соответствующую реализацию [LocationTracker]:
 * - [LocationRuntime.GMS]  → [GmsLocationTracker]
 * - [LocationRuntime.HMS]  → [HmsLocationTracker]
 * - [LocationRuntime.AOSP] → [AospLocationTracker]
 *
 * Вызывается один раз в [LocationTrackingService.startLocationUpdates].
 * Экземпляр трекера хранится в сервисе и освобождается в onDestroy.
 */
object LocationTrackerFactory {

    fun create(context: Context): LocationTracker =
        when (RuntimeDetector.detect(context)) {
            LocationRuntime.GMS  -> GmsLocationTracker(context)
            LocationRuntime.HMS  -> HmsLocationTracker(context)
            LocationRuntime.AOSP -> AospLocationTracker(context)
        }
}
