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
import android.util.Log
import com.example.smarttracker.domain.model.Gender
import com.example.smarttracker.domain.model.LocationPoint
import com.example.smarttracker.domain.model.METActivity
import com.example.smarttracker.domain.repository.LocationRepository
import com.example.smarttracker.domain.repository.WorkoutRepository
import com.example.smarttracker.domain.usecase.CalorieCalculator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.maplibre.android.geometry.LatLng
import java.util.UUID
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
    lateinit var workoutRepository: WorkoutRepository

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

    /**
     * Флаг записи точек в Room. true = штатная запись, false = пауза.
     * @Volatile гарантирует видимость между Main-thread (ViewModel) и IO-thread (callback).
     * GPS-трекер продолжает работать при любом значении флага.
     */
    @Volatile private var isRecording: Boolean = true

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
    /** Job цикла синхронизации GPS-точек с сервером */
    private var syncJob: Job? = null

    // ── Первый GPS-fix и hint-таймер ─────────────────────────────────────────────
    /** true после получения первого хорошего GPS-fix этой сессии */
    private var firstFixDone = false
    /** Подсказка "Выйдите на открытое место" через [LocationConfig.GPS_HINT_TIMEOUT_MS] */
    private var hintJob: Job? = null

    // ── Расчёт калорий (MET-метод) ───────────────────────────────────────────────
    // @Volatile на всех полях: записываются из scope.launch (Dispatchers.IO),
    // читаются из GPS-callback (отдельный поток). Без @Volatile JVM может
    // кэшировать значения в регистрах → GPS-поток видит старый null после записи.
    /** Коррекционный коэффициент CF; вычисляется один раз при старте. null если профиль не заполнен */
    @Volatile private var sessionCF: Double? = null
    @Volatile private var sessionWeightKg: Float? = null
    @Volatile private var sessionAgeYears: Int = 0
    /** MET-данные текущего типа активности (скоростные зоны или базовый MET). null если не загружены */
    @Volatile private var metActivity: METActivity? = null
    /** Предыдущая точка для расчёта интервала. null = первая точка сессии или сразу после паузы */
    @Volatile private var prevCaloriePoint: LocationPoint? = null

    /**
     * true для discovery-сессии (GPS ищется при открытии экрана, до старта тренировки).
     * В этом режиме фильтрация и сглаживание работают (нужны для gpsStatus),
     * но точки НЕ пишутся в Room и НЕ синхронизируются с сервером.
     */
    private var isDiscovery: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        recoveryPrefs = getSharedPreferences(LocationConfig.PREFS_RECOVERY, MODE_PRIVATE)

        // ── Запоздалое обновление профиля (Bug 1 fix) ────────────────────────────
        // ViewModel отправляет этот Intent когда getUserInfo() завершился ПОСЛЕ старта сервиса.
        // Обновляем только поля расчёта калорий — трекинг, буфер и syncLoop не трогаем.
        if (intent?.hasExtra(EXTRA_PROFILE_UPDATE) == true) {
            if (trainingId.isNotBlank() && activeTracker != null) {
                val typeActivId = intent.getIntExtra(EXTRA_TYPE_ACTIV_ID, -1)
                val weightKg    = if (intent.hasExtra(EXTRA_WEIGHT_KG)) intent.getFloatExtra(EXTRA_WEIGHT_KG, 0f) else null
                val heightCm    = if (intent.hasExtra(EXTRA_HEIGHT_CM)) intent.getFloatExtra(EXTRA_HEIGHT_CM, 0f) else null
                val ageYears    = intent.getIntExtra(EXTRA_AGE_YEARS, 0)
                val isMale      = intent.getBooleanExtra(EXTRA_IS_MALE, true)

                sessionWeightKg = weightKg
                sessionAgeYears = ageYears
                // Сбрасываем prevCaloriePoint: первый интервал после загрузки профиля
                // не имеет калорий (нет предыдущей точки с известным временем).
                prevCaloriePoint = null

                if (weightKg != null && heightCm != null && typeActivId >= 0) {
                    scope.launch {
                        sessionCF = CalorieCalculator.computeCF(
                            weightKg, heightCm, ageYears,
                            if (isMale) Gender.MALE else Gender.FEMALE
                        )
                        metActivity = workoutRepository.getMETActivity(typeActivId).getOrNull()
                        Log.d(TAG, "CalorieCalc profile update: CF=$sessionCF, met=$metActivity")
                    }
                }
            }
            return START_STICKY
        }

        // Команда переключения записи: применяем только если сервис уже инициализирован.
        // Если trainingId пустой (сервис убит ОС и перезапущен START_STICKY),
        // команда EXTRA_RECORDING пришла «в пустой» сервис — останавливаемся,
        // чтобы не оставить его в неконсистентном состоянии без трекера/уведомления.
        if (intent?.hasExtra(EXTRA_RECORDING) == true) {
            if (trainingId.isNotBlank() && activeTracker != null) {
                val newRecording = intent.getBooleanExtra(EXTRA_RECORDING, true)
                // Возобновление после паузы: сбрасываем предыдущую точку, чтобы
                // пауза не считалась активным интервалом при расчёте калорий.
                if (newRecording && !isRecording) {
                    prevCaloriePoint = null
                }
                isRecording = newRecording
                return START_STICKY
            } else {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        // При перезапуске системой (START_STICKY) после OOM intent == null.
        // Пробуем восстановить trainingId из SharedPreferences.
        val id = intent?.getStringExtra(EXTRA_TRAINING_ID)
            ?: recoveryPrefs.getString(LocationConfig.KEY_ACTIVE_TRAINING, null)

        if (id.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        trainingId  = id
        isDiscovery = intent?.getBooleanExtra(EXTRA_IS_DISCOVERY, false) ?: false
        accuracyThreshold = intent?.getFloatExtra(
            EXTRA_ACCURACY_THRESHOLD, LocationConfig.MAX_ACCURACY_RUNNING
        ) ?: LocationConfig.MAX_ACCURACY_RUNNING
        val intervalMs = intent?.getLongExtra(EXTRA_INTERVAL_MS, LocationConfig.INTERVAL_MS_RUNNING)
            ?: LocationConfig.INTERVAL_MS_RUNNING

        // Сбрасываем кеш replay finishSyncFlow: новая сессия — старый сигнал завершения
        // предыдущей тренировки не должен быть принят ViewModel текущей тренировки.
        _finishSyncFlow.resetReplayCache()

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

        Log.d(TAG, "onStartCommand: trainingId=$trainingId, interval=${intervalMs}ms")

        startLocationUpdates(intervalMs)
        startFlushTimer()
        startSyncLoop()
        startHintTimer()

        // ── Инициализация расчёта калорий (MET-метод) ────────────────────────────
        // Значения профиля передаются из WorkoutStartViewModel через Intent-экстры.
        // Если weight или height отсутствуют — calories будет null для всех точек.
        val typeActivId = intent?.getIntExtra(EXTRA_TYPE_ACTIV_ID, -1) ?: -1
        val weightKg    = if (intent?.hasExtra(EXTRA_WEIGHT_KG) == true) intent.getFloatExtra(EXTRA_WEIGHT_KG, 0f) else null
        val heightCm    = if (intent?.hasExtra(EXTRA_HEIGHT_CM) == true) intent.getFloatExtra(EXTRA_HEIGHT_CM, 0f) else null
        val ageYears    = intent?.getIntExtra(EXTRA_AGE_YEARS, 0) ?: 0
        val isMale      = intent?.getBooleanExtra(EXTRA_IS_MALE, true) ?: true

        sessionWeightKg  = weightKg
        sessionAgeYears  = ageYears
        prevCaloriePoint = null  // сброс при каждом старте сессии

        if (weightKg != null && heightCm != null && typeActivId >= 0) {
            scope.launch {
                sessionCF = CalorieCalculator.computeCF(
                    weightKg, heightCm, ageYears,
                    if (isMale) Gender.MALE else Gender.FEMALE
                )
                metActivity = workoutRepository.getMETActivity(typeActivId).getOrNull()
                Log.d(TAG, "CalorieCalc init: CF=$sessionCF, met=$metActivity")
            }
        }

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
     * Цикл синхронизации GPS-точек с сервером.
     *
     * Каждые [LocationConfig.SYNC_INTERVAL_MS] читает из Room неотправленные точки,
     * разбивает на батчи по [LocationConfig.GPS_BATCH_MAX_SIZE] и отправляет через
     * WorkoutRepository.uploadGpsPoints(). При успехе — помечает батч как отправленный.
     * При ошибке — ничего не делает, retry на следующем цикле.
     *
     * Ошибки ловятся на уровне каждого батча — один сбой не прерывает остальные батчи.
     */
    private fun startSyncLoop() {
        syncJob?.cancel()
        syncJob = scope.launch {
            while (isActive) {
                delay(LocationConfig.SYNC_INTERVAL_MS)
                try {
                    syncUnsentPoints()
                } catch (e: Exception) {
                    Log.w(TAG, "syncUnsentPoints failed, retry next cycle", e)
                }
            }
        }
    }

    /**
     * Отправляет накопленные в Room неотправленные GPS-точки на сервер.
     * Вызывается периодически из syncLoop и однократно в onDestroy.
     *
     * Идемпотентность: точки с уже назначенным batchId ретраятся с тем же batchId,
     * чтобы сервер мог отклонить дубль. Новые точки получают свежий batchId
     * (DAO обновляет только WHERE batchId IS NULL — повторного присвоения не бывает).
     */
    private suspend fun syncUnsentPoints() {
        if (trainingId.isBlank()) return

        val unsent = locationRepository.getUnsentPoints(trainingId)
        if (unsent.isEmpty()) return

        Log.d(TAG, "syncUnsentPoints: ${unsent.size} unsent points for training=$trainingId")

        // Разделяем точки: с уже назначенным batchId (ретрай) и без него (новые).
        val (withBatchId, withoutBatchId) = unsent.partition { it.batchId != null }

        // Точки с уже назначенным batchId: ретрай со старым ID (не генерируем новый,
        // иначе сервер воспримет как новый батч и создаст дубли).
        val existingBatches = withBatchId.groupBy {
            checkNotNull(it.batchId) { "withBatchId partition contains point with null batchId: id=${it.id}" }
        }
        existingBatches.forEach { (batchId, points) ->
            try {
                workoutRepository.uploadGpsPoints(trainingId, batchId, points)
                    .onSuccess { saved ->
                        Log.d(TAG, "GPS batch retry: $saved points, batchId=$batchId")
                        locationRepository.markBatchAsSent(batchId)
                    }
                    .onFailure { e ->
                        Log.w(TAG, "GPS batch retry failed, batchId=$batchId", e)
                    }
            } catch (e: Exception) {
                Log.w(TAG, "GPS sync retry chunk error", e)
            }
        }

        // Новые точки (batchId == null): назначаем свежий batchId и отправляем.
        // DAO гарантирует WHERE batchId IS NULL — уже назначенные не перезаписываются.
        withoutBatchId.chunked(LocationConfig.GPS_BATCH_MAX_SIZE).forEach { chunk ->
            try {
                val batchId = UUID.randomUUID().toString()
                val pointIds = chunk.map { it.id }
                locationRepository.assignBatchId(pointIds, batchId)

                workoutRepository.uploadGpsPoints(trainingId, batchId, chunk)
                    .onSuccess { saved ->
                        Log.d(TAG, "GPS batch uploaded: $saved points, batchId=$batchId")
                        locationRepository.markBatchAsSent(batchId)
                    }
                    .onFailure { e ->
                        Log.w(TAG, "GPS batch upload failed, batchId=$batchId", e)
                    }
            } catch (e: Exception) {
                Log.w(TAG, "GPS sync chunk error", e)
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

        // ── Запись в буфер только во время активного трекинга ───────────────────
        // При isRecording = false GPS-трекер продолжает работать: фильтры, сглаживание
        // и lastAcceptedLocation обновляются выше — это нужно чтобы не было «прыжков»
        // после снятия паузы. Точки в Room не попадают.
        if (!isRecording) return

        // Discovery-режим: точки НЕ пишутся в Room и НЕ синхронизируются.
        // Фильтрация и сглаживание выполнены выше — это нужно для gpsStatus (ACQUIRED).
        // Запись в Room запрещена: discovery-UUID никогда не регистрируется на сервере,
        // поэтому syncUnsentPoints() получил бы 404 на каждом батче.
        if (isDiscovery) return

        // ── Bearing guard: при медленном движении пеленг ненадёжен ──────────────
        val bearing: Float? = if (
            smoothed.hasSpeed() &&
            smoothed.speed >= LocationConfig.MIN_SPEED_FOR_BEARING_MPS &&
            smoothed.hasBearing()
        ) smoothed.bearing else null

        val rawPoint = LocationPoint(
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

        // Вычислить калории за интервал от предыдущей точки до этой.
        // prevCaloriePoint == null → первая точка сессии или первая после паузы → calories = null.
        val calories = computeCaloriesForPoint(rawPoint)
        val point = rawPoint.copy(calories = calories)
        Log.d(TAG, "point: calories=${point.calories}, speed=${point.speed}")
        // Обновляем опорную точку ПОСЛЕ copy, чтобы использовать уже финальный объект.
        prevCaloriePoint = point

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
     * Вычисляет расход калорий за интервал от [prevCaloriePoint] до [current] (MET-метод).
     *
     * Возвращает null если:
     * - профиль пользователя не инициализирован (sessionCF == null)
     * - это первая точка сессии или первая после паузы (prevCaloriePoint == null)
     * - временной интервал <= 0 (нарушение монотонности часов)
     *
     * Для скоростных зон использует линейную интерполяцию из [CalorieCalculator.interpolateMet].
     * Для пользователей 60+ применяет специальную формулу [CalorieCalculator.energyOver60].
     */
    private fun computeCaloriesForPoint(current: LocationPoint): Double? {
        val cf     = sessionCF        ?: return null
        val weight = sessionWeightKg  ?: return null
        val prev   = prevCaloriePoint ?: return null
        val met    = metActivity      ?: return null

        val durationMin = (current.timestampUtc - prev.timestampUtc) / 60_000.0
        if (durationMin <= 0) return null

        // Android Location.speed — м/с; MET-зоны ожидают км/ч
        val speedKmh = (current.speed ?: 0f) * 3.6
        val metValue = if (met.usesSpeedZones)
            CalorieCalculator.interpolateMet(speedKmh, met.zones)
        else
            met.baseMet

        return if (sessionAgeYears >= 60)
            CalorieCalculator.energyOver60(metValue, weight, durationMin)
        else
            CalorieCalculator.energyForInterval(metValue, cf, weight, durationMin)
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
        locationRepository.savePoints(batch)
        pointBuffer.subList(0, batch.size).clear()
    }

    override fun onDestroy() {
        super.onDestroy()
        activeTracker?.stopTracking()
        activeTracker = null
        hintJob?.cancel()
        flushTimerJob?.cancel()
        syncJob?.cancel()

        // Финальный сброс буфера + попытка синхронизации оставшихся точек
        scope.launch {
            try {
                bufferMutex.withLock { flushBufferLocked() }
                syncUnsentPoints()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to flush and sync unsent points during service shutdown", e)
            } finally {
                // Сигнализируем ViewModel: финальный sync завершён — можно закрывать тренировку
                _finishSyncFlow.tryEmit(Unit)
                scope.cancel()
            }
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
        private const val TAG = "LocationTrackingService"

        // ── Intent extras ─────────────────────────────────────────────────────────
        const val EXTRA_TRAINING_ID        = "training_id"
        const val EXTRA_INTERVAL_MS        = "interval_ms"
        const val EXTRA_ACCURACY_THRESHOLD = "accuracy_threshold"

        // ── Extras профиля пользователя для расчёта калорий (MET-метод) ──────────
        /** ID типа активности (type_activ_id) для загрузки MET-данных */
        const val EXTRA_TYPE_ACTIV_ID = "extra_type_activ_id"
        /** Вес пользователя в кг. Отсутствие extra → calories = null */
        const val EXTRA_WEIGHT_KG     = "extra_weight_kg"
        /** Рост пользователя в см. Отсутствие extra → calories = null */
        const val EXTRA_HEIGHT_CM     = "extra_height_cm"
        /** Возраст пользователя в годах (рассчитывается в ViewModel) */
        const val EXTRA_AGE_YEARS     = "extra_age_years"
        /** true = мужской пол; используется для CF (формула Харриса-Бенедикта) */
        const val EXTRA_IS_MALE       = "extra_is_male"

        // ── Intent actions ─────────────────────────────────────────────────────────
        /** Запустить трекинг (передаётся через startForegroundService) */
        const val ACTION_START = "com.example.smarttracker.action.LOCATION_START"
        /** Остановить трекинг (передаётся через stopService) */
        const val ACTION_STOP  = "com.example.smarttracker.action.LOCATION_STOP"

        /** Размер окна скользящего среднего для сглаживания GPS-шума */
        private const val SMOOTH_WINDOW_SIZE = 3

        const val EXTRA_RECORDING = "extra_recording"

        /**
         * Команда запоздалого обновления профиля в работающий сервис.
         * Отправляется из WorkoutStartViewModel когда getUserInfo() завершается
         * уже после старта тренировки (race condition fix).
         * Сервис обновляет sessionCF и metActivity без перезапуска трекинга.
         */
        const val EXTRA_PROFILE_UPDATE = "extra_profile_update"

        /**
         * Флаг discovery-режима: GPS ищется при открытии экрана, до старта тренировки.
         * При true: точки не пишутся в Room, не синхронизируются с сервером.
         */
        const val EXTRA_IS_DISCOVERY = "extra_is_discovery"

        /**
         * Сигнал завершения финального flush+sync при остановке сервиса.
         * ViewModel ждёт этот сигнал перед вызовом saveTraining, чтобы
         * гарантировать что все GPS-точки уже отправлены до закрытия тренировки.
         * replay=1: если ViewModel подписалась после emit — сигнал не потеряется.
         */
        private val _finishSyncFlow = MutableSharedFlow<Unit>(
            replay = 1,
            extraBufferCapacity = 0,
        )
        val finishSyncFlow: SharedFlow<Unit> = _finishSyncFlow.asSharedFlow()

        /**
         * Отправляет Intent с флагом записи в уже запущенный сервис.
         * Сервис обрабатывает его в [onStartCommand] без повторной инициализации.
         */
        fun setRecording(context: android.content.Context, recording: Boolean) {
            val intent = Intent(context, LocationTrackingService::class.java)
                .putExtra(EXTRA_RECORDING, recording)
            context.startService(intent)
        }
    }
}
