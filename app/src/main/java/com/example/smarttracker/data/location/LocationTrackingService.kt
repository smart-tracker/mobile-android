package com.example.smarttracker.data.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.smarttracker.R
import com.example.smarttracker.data.location.model.TrackingConfig
import com.example.smarttracker.data.location.model.TrackingPriority
import com.example.smarttracker.data.location.model.toAndroidLocation
import com.example.smarttracker.data.location.tracker.LocationTracker
import com.example.smarttracker.domain.model.LocationPoint
import com.example.smarttracker.domain.repository.LocationRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.maplibre.android.geometry.LatLng
import javax.inject.Inject

/**
 * Foreground Service для сбора GPS-координат тренировки.
 *
 * Работает через LocationTrackerFactory (GMS / HMS / AOSP в зависимости от устройства).
 * Каждая принятая точка проходит многослойную фильтрацию и накапливается в in-memory буфере.
 * Буфер сбрасывается в Room batch-операцией по размеру ([LocationConfig.BUFFER_FLUSH_SIZE])
 * или по таймеру ([LocationConfig.BUFFER_FLUSH_INTERVAL_MS]).
 *
 * **Crash-recovery:** trainingId сохраняется в SharedPreferences на момент старта.
 * При убийстве процесса ОС (OOM killer) и перезапуске через START_STICKY
 * сервис читает trainingId из префов и продолжает запись в туже тренировку.
 *
 * **Многослойная фильтрация GPS-точек:**
 * 1. Слой 1 — погрешность (accuracy > threshold → reject)
 * 2. Слой 2 — минимальный интервал по времени (< [LocationConfig.MIN_TIME_BETWEEN_UPDATES_MS] → reject)
 * 3. Слой 3 — телепортация (скорость > [LocationConfig.MAX_REALISTIC_SPEED_MPS] → reject)
 * 4. Слой 4 — антидребезг по расстоянию (< [LocationConfig.MIN_DISTANCE_ANTIJITTER_M] → reject если не стоим)
 *
 * **Moving Average сглаживание:** скользящее среднее по последним 3 точкам (lat/lng)
 * уменьшает шум GPS без задержки, характерной для Калмана.
 */
@AndroidEntryPoint
class LocationTrackingService : Service() {

    @Inject
    lateinit var locationRepository: LocationRepository

    @Inject
    lateinit var offlineMapManager: OfflineMapManager

    // SupervisorJob: сбой одной корутины не отменяет остальные
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * PARTIAL_WAKE_LOCK удерживает CPU активным при выключенном экране.
     * Timeout [LocationConfig.WAKELOCK_TIMEOUT_MS] — защита от бесконечного удержания
     * при краше. Foreground Service + foregroundServiceType="location" освобождён
     * от Doze, но WakeLock дополнительно гарантирует работу CPU на старых API (26–28).
     */
    private var wakeLock: PowerManager.WakeLock? = null

    /**
     * Активный трекер геолокации (GMS / HMS / AOSP).
     * Создаётся в [startLocationUpdates] через [LocationTrackerFactory].
     */
    private var activeTracker: LocationTracker? = null

    private var trainingId: String = ""
    private var accuracyThreshold: Float = LocationConfig.MAX_ACCURACY_RUNNING

    // ── Crash-recovery ───────────────────────────────────────────────────────────
    private lateinit var recoveryPrefs: SharedPreferences

    // ── Фильтрация слоёв 2–4 ────────────────────────────────────────────────────
    /** Последняя принятая (прошедшая фильтры) точка — используется для расчёта дельты */
    private var lastAcceptedLocation: Location? = null

    // ── Moving Average сглаживание ───────────────────────────────────────────────
    /**
     * Кольцевой буфер последних [SMOOTH_WINDOW_SIZE] точек для скользящего среднего.
     * Сглаживает шум GPS без введения значительной задержки.
     */
    private val smoothingWindow = ArrayDeque<Location>(SMOOTH_WINDOW_SIZE)

    // ── In-memory буфер + Mutex ──────────────────────────────────────────────────
    private val pointBuffer = mutableListOf<LocationPoint>()
    private val bufferMutex = Mutex()
    private var flushTimerJob: Job? = null

    // ── Первый GPS-fix и hint-таймер ─────────────────────────────────────────────
    /** true после получения первого хорошего GPS-fix этой сессии */
    private var firstFixDone = false
    /** Подсказка "Выйдите на открытое место" через [LocationConfig.GPS_HINT_TIMEOUT_MS] */
    private var hintJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        recoveryPrefs = getSharedPreferences(LocationConfig.PREFS_RECOVERY, MODE_PRIVATE)

        // При перезапуске системой (START_STICKY) после OOM intent == null.
        // Пробуем восстановить trainingId из SharedPreferences.
        val id = intent?.getStringExtra(EXTRA_TRAINING_ID)
            ?: recoveryPrefs.getString(LocationConfig.KEY_ACTIVE_TRAINING, null)

        if (id.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        trainingId = id
        accuracyThreshold = intent?.getFloatExtra(
            EXTRA_ACCURACY_THRESHOLD, LocationConfig.MAX_ACCURACY_RUNNING
        ) ?: LocationConfig.MAX_ACCURACY_RUNNING
        val intervalMs = intent?.getLongExtra(EXTRA_INTERVAL_MS, LocationConfig.INTERVAL_MS_RUNNING)
            ?: LocationConfig.INTERVAL_MS_RUNNING

        // Сохраняем для crash-recovery до старта трекинга
        recoveryPrefs.edit().putString(LocationConfig.KEY_ACTIVE_TRAINING, trainingId).apply()

        createNotificationChannel()
        val notification = buildNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                LocationConfig.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(LocationConfig.NOTIFICATION_ID, notification)
        }

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SmartTracker:LocationTracking",
        ).also { it.acquire(LocationConfig.WAKELOCK_TIMEOUT_MS) }

        startLocationUpdates(intervalMs)
        startFlushTimer()
        startHintTimer()

        return START_STICKY
    }

    private fun startLocationUpdates(intervalMs: Long) {
        val config = TrackingConfig(
            intervalMs        = intervalMs,
            minDistanceMeters = LocationConfig.MIN_DISTANCE_M,
            priority          = TrackingPriority.HIGH_ACCURACY,
        )
        val tracker = LocationTrackerFactory.create(this)
        activeTracker = tracker
        tracker.startTracking(config) { trackLoc ->
            onLocationReceived(trackLoc.toAndroidLocation())
        }
    }

    /**
     * Таймер периодического сброса буфера. Гарантирует запись даже если буфер
     * не достигает [LocationConfig.BUFFER_FLUSH_SIZE] (медленная ходьба, пауза).
     */
    private fun startFlushTimer() {
        flushTimerJob?.cancel()
        flushTimerJob = scope.launch {
            while (isActive) {
                delay(LocationConfig.BUFFER_FLUSH_INTERVAL_MS)
                flushBuffer()
            }
        }
    }

    /**
     * Мягкий таймер подсказки: через [LocationConfig.GPS_HINT_TIMEOUT_MS] после старта
     * обновляем уведомление если fix так и не получен.
     * (Ухудшение статуса до UNAVAILABLE происходит через WorkoutStartViewModel через 30 сек.)
     */
    private fun startHintTimer() {
        hintJob?.cancel()
        hintJob = scope.launch {
            delay(LocationConfig.GPS_HINT_TIMEOUT_MS)
            // Показываем подсказку в уведомлении если fix ещё не получен
            if (!firstFixDone) {
                val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                val hint = NotificationCompat.Builder(this@LocationTrackingService, LocationConfig.CHANNEL_ID)
                    .setContentTitle("SmartTracker")
                    .setContentText("Поиск GPS... Выйдите на открытое место")
                    .setSmallIcon(R.drawable.ic_activity_running)
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build()
                manager.notify(LocationConfig.NOTIFICATION_ID, hint)
            }
        }
    }

    /**
     * Основной обработчик новой GPS-точки. Проходит 4 слоя фильтрации,
     * применяет Moving Average сглаживание и помещает в буфер.
     *
     * Все фильтры работают на Main-thread (callback от OS/GMS), поэтому
     * обращение к lastAcceptedLocation без дополнительной синхронизации безопасно.
     */
    private fun onLocationReceived(location: Location) {
        // ── Слой 1: фильтр по погрешности ───────────────────────────────────────
        if (location.hasAccuracy() && location.accuracy > accuracyThreshold) return

        val prev = lastAcceptedLocation

        // ── Слой 2: минимальный интервал по времени ──────────────────────────────
        if (prev != null) {
            val dtMs = location.time - prev.time
            if (dtMs < LocationConfig.MIN_TIME_BETWEEN_UPDATES_MS) return
        }

        // ── Слой 3: проверка на телепортацию (нереалистичная скорость) ───────────
        if (prev != null) {
            val distM   = prev.distanceTo(location)
            val dtSec   = (location.time - prev.time) / 1000.0
            if (dtSec > 0) {
                val speedMps = distM / dtSec
                if (speedMps > LocationConfig.MAX_REALISTIC_SPEED_MPS) return
            }
        }

        // ── Слой 4: антидребезг по расстоянию ───────────────────────────────────
        // Пропускаем если устройство фактически не двигалось.
        // Первая точка (prev == null) проходит всегда.
        if (prev != null) {
            val distM = prev.distanceTo(location)
            if (distM < LocationConfig.MIN_DISTANCE_ANTIJITTER_M) return
        }

        // ── Moving Average сглаживание ───────────────────────────────────────────
        smoothingWindow.addLast(location)
        if (smoothingWindow.size > SMOOTH_WINDOW_SIZE) smoothingWindow.removeFirst()
        val smoothed = if (smoothingWindow.size == SMOOTH_WINDOW_SIZE) {
            applyMovingAverage(location)
        } else {
            location   // пока окно не заполнено — сырая точка
        }

        // ── Первый хороший fix ───────────────────────────────────────────────────
        if (!firstFixDone) {
            firstFixDone = true
            hintJob?.cancel()
            // Восстанавливаем стандартное уведомление
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(LocationConfig.NOTIFICATION_ID, buildNotification())
            offlineMapManager.downloadRegionIfNeeded(
                LatLng(smoothed.latitude, smoothed.longitude),
                isWifiConnected(),
            )
        }

        lastAcceptedLocation = smoothed

        // ── Bearing guard: при медленном движении пеленг ненадёжен ──────────────
        val bearing: Float? = if (
            smoothed.hasSpeed() &&
            smoothed.speed >= LocationConfig.MIN_SPEED_FOR_BEARING_MPS &&
            smoothed.hasBearing()
        ) smoothed.bearing else null

        val point = LocationPoint(
            trainingId   = trainingId,
            timestampUtc = smoothed.time,
            elapsedNanos = smoothed.elapsedRealtimeNanos,
            latitude     = smoothed.latitude,
            longitude    = smoothed.longitude,
            altitude     = if (smoothed.hasAltitude()) smoothed.altitude else null,
            speed        = if (smoothed.hasSpeed()) smoothed.speed else null,
            accuracy     = if (smoothed.hasAccuracy()) smoothed.accuracy else null,
            bearing      = bearing,
        )

        scope.launch {
            bufferMutex.withLock {
                pointBuffer.add(point)
                if (pointBuffer.size >= LocationConfig.BUFFER_FLUSH_SIZE) {
                    flushBufferLocked()
                }
            }
        }
    }

    /**
     * Применяет скользящее среднее к текущей точке на основе [smoothingWindow].
     * Возвращает новый Location с усреднёнными lat/lng; остальные поля берутся из [current].
     */
    private fun applyMovingAverage(current: Location): Location {
        val avgLat = smoothingWindow.sumOf { it.latitude }  / smoothingWindow.size
        val avgLng = smoothingWindow.sumOf { it.longitude } / smoothingWindow.size
        return Location(current).apply {
            latitude  = avgLat
            longitude = avgLng
        }
    }

    /**
     * Сбрасывает буфер в Room. Вызывается снаружи — захватывает Mutex самостоятельно.
     */
    private fun flushBuffer() {
        scope.launch {
            bufferMutex.withLock {
                flushBufferLocked()
            }
        }
    }

    /**
     * Вставка накопленного буфера в Room batch-операцией.
     * Вызывать только под [bufferMutex].
     */
    private suspend fun flushBufferLocked() {
        if (pointBuffer.isEmpty()) return
        val batch = pointBuffer.toList()
        pointBuffer.clear()
        locationRepository.savePoints(batch)
    }

    override fun onDestroy() {
        super.onDestroy()
        activeTracker?.stopTracking()
        activeTracker = null
        hintJob?.cancel()
        flushTimerJob?.cancel()

        // Финальный сброс буфера: записываем все накопленные точки перед остановкой
        scope.launch {
            bufferMutex.withLock { flushBufferLocked() }
        }.invokeOnCompletion {
            scope.cancel()
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        firstFixDone = false
        lastAcceptedLocation = null
        smoothingWindow.clear()

        // Очищаем crash-recovery — тренировка завершена штатно
        if (::recoveryPrefs.isInitialized) {
            recoveryPrefs.edit().remove(LocationConfig.KEY_ACTIVE_TRAINING).apply()
        }

        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun isWifiConnected(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            LocationConfig.CHANNEL_ID,
            "Трекинг тренировки",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Уведомление активной тренировки"
            setShowBadge(false)
        }
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, LocationConfig.CHANNEL_ID)
            .setContentTitle("SmartTracker")
            .setContentText("Тренировка идёт")
            .setSmallIcon(R.drawable.ic_activity_running)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    companion object {
        // ── Intent extras ─────────────────────────────────────────────────────────
        const val EXTRA_TRAINING_ID        = "training_id"
        const val EXTRA_INTERVAL_MS        = "interval_ms"
        const val EXTRA_ACCURACY_THRESHOLD = "accuracy_threshold"

        // ── Intent actions ─────────────────────────────────────────────────────────
        /** Запустить трекинг (передаётся через startForegroundService) */
        const val ACTION_START = "com.example.smarttracker.action.LOCATION_START"
        /** Остановить трекинг (передаётся через stopService) */
        const val ACTION_STOP  = "com.example.smarttracker.action.LOCATION_STOP"

        /** Размер окна скользящего среднего для сглаживания GPS-шума */
        private const val SMOOTH_WINDOW_SIZE = 3
    }
}
