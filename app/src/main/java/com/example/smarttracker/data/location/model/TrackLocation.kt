package com.example.smarttracker.data.location.model

import android.location.Location

/**
 * Независимая от SDK модель GPS-точки.
 *
 * Не содержит импортов из GMS, HMS или android.location — это позволяет
 * использовать её в любом слое без риска NoClassDefFoundError на устройствах,
 * где один из SDK отсутствует.
 */
data class TrackLocation(
    val lat: Double,
    val lon: Double,
    /** Горизонтальная погрешность, метры. 0f если датчик не сообщил. */
    val accuracy: Float,
    /** Скорость, м/с. 0f если датчик не сообщил. */
    val speed: Float,
    /** Азимут движения, градусы [0, 360). 0f если датчик не сообщил. */
    val bearing: Float,
    /** Высота над уровнем моря, метры. 0.0 если датчик не сообщил. */
    val altitude: Double,
    /** UTC-время фикса, мс с Unix-эпохи. */
    val timestamp: Long,
    /** Монотонное время с загрузки устройства, нс. Не зависит от смены часового пояса/NTP. */
    val elapsedRealtimeNanos: Long,
)

/**
 * Преобразование стандартного [android.location.Location] в [TrackLocation].
 *
 * Используется всеми тремя реализациями трекера (AOSP, GMS, HMS) — все они
 * в итоге получают стандартный android.location.Location из ОС.
 */
fun Location.toTrackLocation(): TrackLocation = TrackLocation(
    lat                 = latitude,
    lon                 = longitude,
    accuracy            = if (hasAccuracy()) accuracy else 0f,
    speed               = if (hasSpeed()) speed else 0f,
    bearing             = if (hasBearing()) bearing else 0f,
    altitude            = if (hasAltitude()) altitude else 0.0,
    timestamp           = time,
    elapsedRealtimeNanos = elapsedRealtimeNanos,
)

/**
 * Обратное преобразование [TrackLocation] → [android.location.Location].
 *
 * Нужно чтобы не трогать существующую цепочку обработки в [LocationTrackingService]
 * (фильтрация accuracy, запись в Room), которая принимает android.location.Location.
 * Провайдер "tracker" — произвольная строка-метка, в логике не используется.
 */
fun TrackLocation.toAndroidLocation(): Location =
    Location("tracker").apply {
        latitude             = this@toAndroidLocation.lat
        longitude            = this@toAndroidLocation.lon
        accuracy             = this@toAndroidLocation.accuracy
        speed                = this@toAndroidLocation.speed
        bearing              = this@toAndroidLocation.bearing
        altitude             = this@toAndroidLocation.altitude
        time                 = this@toAndroidLocation.timestamp
        elapsedRealtimeNanos = this@toAndroidLocation.elapsedRealtimeNanos
    }
