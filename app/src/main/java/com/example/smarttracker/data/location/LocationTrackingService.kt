package com.example.smarttracker.data.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng
import javax.inject.Inject

/**
 * Foreground Service для сбора GPS-координат тренировки.
 *
 * Работает через стандартный android.location.LocationManager (GPS_PROVIDER).
 * Каждая принятая точка с погрешностью ≤ порога записывается в Room через
 * LocationRepository. Service живёт независимо от UI — данные продолжают
 * записываться при заблокированном экране.
 *
 * Жизненный цикл: запускается из WorkoutStartViewModel через startForegroundService(),
 * останавливается через stopService() при паузе или завершении тренировки.
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
     * Без него LocationManager может не доставлять колбэки когда телефон засыпает.
     * Foreground Service с foregroundServiceType="location" освобождён от Doze,
     * но WakeLock дополнительно гарантирует работу CPU на старых устройствах (API 26–28).
     */
    private var wakeLock: PowerManager.WakeLock? = null

    /**
     * Активный трекер геолокации (GMS / HMS / AOSP).
     * Создаётся в [startLocationUpdates] через [LocationTrackerFactory],
     * освобождается в [onDestroy].
     */
    private var activeTracker: LocationTracker? = null

    private var trainingId: String = ""
    private var accuracyThreshold: Float = LocationConfig.MAX_ACCURACY_RUNNING
    // Флаг первого хорошего GPS-fix — триггер для тихой предзагрузки офлайн-карты
    private var firstFixDone = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // При перезапуске системой после OOM intent будет null — ничего не делаем,
        // вызывающая сторона (ViewModel) должна перезапустить трекинг явно.
        val id = intent?.getStringExtra(EXTRA_TRAINING_ID)
        if (id.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        trainingId = id
        accuracyThreshold = intent.getFloatExtra(EXTRA_ACCURACY_THRESHOLD, LocationConfig.MAX_ACCURACY_RUNNING)
        val intervalMs = intent.getLongExtra(EXTRA_INTERVAL_MS, LocationConfig.INTERVAL_MS_RUNNING)

        // Notification channel нужно создавать каждый раз — повторный вызов безопасен
        createNotificationChannel()
        val notification = buildNotification()

        // FOREGROUND_SERVICE_TYPE_LOCATION обязателен начиная с Android Q (API 29),
        // когда тип объявлен в AndroidManifest через android:foregroundServiceType="location"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                LocationConfig.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(LocationConfig.NOTIFICATION_ID, notification)
        }

        // Захватываем WakeLock после startForeground — CPU не засыпает при экране off
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "SmartTracker:LocationTracking",
        ).also { it.acquire() }

        startLocationUpdates(intervalMs)
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
        // onLocationReceived не изменяется — получает стандартный android.location.Location
        // через обратный маппинг toAndroidLocation() для совместимости с фильтрацией / Room.
        tracker.startTracking(config) { trackLoc ->
            onLocationReceived(trackLoc.toAndroidLocation())
        }
    }

    private fun onLocationReceived(location: Location) {
        // Фильтр по погрешности: точки хуже порога отбрасываются.
        // hasAccuracy() = false бывает только на очень старых устройствах — принимаем как есть.
        if (location.hasAccuracy() && location.accuracy > accuracyThreshold) return

        // При первом хорошем fix — тихо запустить предзагрузку офлайн-региона (только при Wi-Fi)
        if (!firstFixDone) {
            firstFixDone = true
            offlineMapManager.downloadRegionIfNeeded(
                LatLng(location.latitude, location.longitude),
                isWifiConnected(),
            )
        }

        val point = LocationPoint(
            trainingId    = trainingId,
            timestampUtc  = location.time,
            elapsedNanos  = location.elapsedRealtimeNanos,
            latitude      = location.latitude,
            longitude     = location.longitude,
            altitude      = if (location.hasAltitude()) location.altitude else null,
            speed         = if (location.hasSpeed()) location.speed else null,
            accuracy      = if (location.hasAccuracy()) location.accuracy else null,
        )

        scope.launch {
            locationRepository.savePoint(point)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activeTracker?.stopTracking()
        activeTracker = null
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        firstFixDone = false
        // Освобождаем WakeLock только если он ещё удерживается — isHeld защищает от двойного release
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    /**
     * Проверяет наличие Wi-Fi соединения. Используется перед запуском предзагрузки
     * офлайн-региона — скачиваем только при Wi-Fi, чтобы не расходовать мобильный трафик.
     */
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
            NotificationManager.IMPORTANCE_LOW,  // LOW = без звука, но видна в шторке
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
            .setOngoing(true)          // non-dismissible
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    companion object {
        const val EXTRA_TRAINING_ID        = "training_id"
        const val EXTRA_INTERVAL_MS        = "interval_ms"
        const val EXTRA_ACCURACY_THRESHOLD = "accuracy_threshold"
    }
}
