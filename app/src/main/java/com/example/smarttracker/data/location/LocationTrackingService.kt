package com.example.smarttracker.data.location

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.example.smarttracker.R
import com.example.smarttracker.domain.model.LocationPoint
import com.example.smarttracker.domain.repository.LocationRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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

    // SupervisorJob: сбой одной корутины не отменяет остальные
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var locationManager: LocationManager
    private var trainingId: String = ""
    private var accuracyThreshold: Float = LocationConfig.MAX_ACCURACY_RUNNING

    /**
     * LocationListener на основе android.location (не FusedLocationProvider).
     * onLocationChanged вызывается на Main Looper (см. requestLocationUpdates ниже),
     * тяжёлая работа (сохранение в БД) выполняется в scope (Dispatchers.IO).
     */
    private val locationListener = LocationListener { location ->
        onLocationReceived(location)
    }

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

        startLocationUpdates(intervalMs)
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates(intervalMs: Long) {
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            // GPS отключён на устройстве — завершаем сервис.
            // ViewModel обнаружит отсутствие точек через 30-секундный таймаут (UNAVAILABLE).
            stopSelf()
            return
        }

        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                intervalMs,
                LocationConfig.MIN_DISTANCE_M,
                locationListener,
                Looper.getMainLooper(),
            )
        } catch (e: SecurityException) {
            // Разрешение было отозвано после запуска сервиса
            stopSelf()
        }
    }

    private fun onLocationReceived(location: Location) {
        // Фильтр по погрешности: точки хуже порога отбрасываются.
        // hasAccuracy() = false бывает только на очень старых устройствах — принимаем как есть.
        if (location.hasAccuracy() && location.accuracy > accuracyThreshold) return

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
        // removeUpdates безопасен даже если requestLocationUpdates не был вызван
        if (::locationManager.isInitialized) {
            locationManager.removeUpdates(locationListener)
        }
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
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
