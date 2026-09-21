package com.example.smarttracker.data.location

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.smarttracker.R
import com.example.smarttracker.data.hrm.HrmManager
import com.example.smarttracker.data.local.SettingsStorage
import com.example.smarttracker.presentation.MainActivity
import com.example.smarttracker.utils.formatHhMmSs
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
import com.example.smarttracker.domain.usecase.CalculateTrainingStatsUseCase
import com.example.smarttracker.domain.usecase.CalorieCalculator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
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
/**
 * Событие смены состояния записи, эмитимое сервисом для синхронизации с ViewModel.
 *
 * @param isRecording        новое состояние: true = запись идёт, false = пауза.
 * @param recordedPointCount число точек тренировки, записанных к этому моменту.
 *                           На паузе равно индексу, по которому ляжет первая пост-резюм
 *                           точка — ViewModel использует его как точный gap-индекс.
 */
data class RecordingState(
    val isRecording: Boolean,
    val recordedPointCount: Int,
)

@OptIn(ExperimentalCoroutinesApi::class)
@AndroidEntryPoint
class LocationTrackingService : Service() {

    @Inject
    lateinit var locationRepository: LocationRepository

    @Inject
    lateinit var workoutRepository: WorkoutRepository

    @Inject
    lateinit var offlineMapManager: OfflineMapManager

    @Inject
    lateinit var settingsStorage: SettingsStorage

    /**
     * Менеджер BLE-пульсометра (singleton — делится с экраном «Датчики»).
     * Сервис лишь управляет им: автоподключение при старте тренировки
     * ([connectHrmIfConfigured]), пульс в точки ([HrmManager.freshBpm]),
     * разрыв в [onDestroy].
     */
    @Inject
    lateinit var hrmManager: HrmManager

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

    // ── Автопауза ────────────────────────────────────────────────────────────────
    /**
     * true = текущая пауза поставлена автопаузой. Авто-резюм разрешён только для
     * такой паузы: ручную (кнопка UI/notification) снимает только пользователь.
     * Любая ручная команда EXTRA_RECORDING сбрасывает флаг.
     */
    @Volatile private var pausedByAuto: Boolean = false

    /** Настройка «Автопауза» из [SettingsStorage]; обновляется подпиской в [onCreate]. */
    @Volatile private var autopauseEnabled: Boolean = false

    /** Распознаёт остановку/возобновление движения по скоростям точек. */
    private val autopauseDetector = AutopauseDetector()

    // ── Голосовые подсказки (TTS) ────────────────────────────────────────────────
    // TTS живёт в сервисе, не во ViewModel: запись идёт с погашенным экраном,
    // ViewModel может не существовать — подсказки должны звучать всё равно.
    /** Настройки из [SettingsStorage]; обновляются подпиской в [onCreate]. */
    @Volatile private var voiceCuesEnabled: Boolean = false
    @Volatile private var voiceCueIntervalKm: Int = 1

    private var tts: android.speech.tts.TextToSpeech? = null

    /** true после успешной инициализации TTS с русским голосом. До готовности
     *  (или при отсутствии голоса на устройстве) фразы молча дропаются. */
    @Volatile private var ttsReady: Boolean = false

    /** Решает, когда объявлять километровый рубеж, и считает темп круга. */
    private val milestoneTracker = VoiceCueMilestoneTracker()

    /**
     * Накопленная дистанция записанных точек (м). ViewModel ведёт свой счётчик
     * для UI; сервису нужен собственный — километровые объявления должны работать
     * и с мёртвым ViewModel (экран погашен).
     */
    @Volatile private var accumulatedDistanceM: Double = 0.0

    /** Предыдущая записанная точка для приращения дистанции.
     *  null после resume/рестарта — телепорт через паузу дистанцией не считается. */
    private var prevDistancePoint: LocationPoint? = null

    /** Haversine с фильтром accuracy — тот же расчёт, что в live-дистанции ViewModel. */
    private val statsUseCase = CalculateTrainingStatsUseCase()

    /** Аудиофокус с приглушением музыки на время фразы (см. [speak]). */
    private var audioFocusRequest: android.media.AudioFocusRequest? = null

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

    // ── Состояние для rich-уведомления (chronometer + скорость) ──────────────────
    // @Volatile: записываются из onStartCommand (Main), читаются из GPS-callback'а
    // (другой поток через scope.launch).
    /** Момент старта или последнего resume (wall-clock ms). Используется как `setWhen` для chronometer. */
    @Volatile private var sessionStartedAt: Long = 0L
    /** Накопленное время до текущей паузы (мс). Сбрасывается на старте, увеличивается при каждой паузе. */
    @Volatile private var pausedAccumulatedMs: Long = 0L
    /** Последняя принятая скорость (м/с) для отображения в уведомлении. null = пока неизвестна. */
    @Volatile private var lastSpeedMps: Float? = null

    /**
     * Точное число GPS-точек тренировки, записанных (или поставленных в буфер на запись) в Room.
     *
     * Источник истины для gap-индекса паузы: ViewModel не может надёжно вычислить его
     * из размера наблюдаемого списка (тот отстаёт от in-memory буфера сервиса на 1-2 точки).
     * При паузе сервис эмитит это значение — индекс, по которому ляжет первая пост-резюм точка.
     *
     * Считаются только точки тренировки (`!isDiscovery`). Сбрасывается при старте тренировки.
     */
    @Volatile private var recordedPointCount: Int = 0

    /**
     * true с момента входа в [onDestroy]. Финальный flush буфера выполняется асинхронно
     * (scope.launch) и может завершиться ПОСЛЕ синхронной очистки recoveryPrefs в onDestroy.
     * Флаг не даёт [flushBufferLocked] заново записать chronometer-ключи поверх очистки.
     */
    @Volatile private var isShuttingDown: Boolean = false

    /** Системный NotificationManager — кешируется, вызывается на каждой GPS-точке. */
    private val notificationManager: NotificationManager by lazy {
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    /**
     * PendingIntent тапа по телу уведомления — открывает MainActivity.
     * Содержимое неизменно, поэтому кешируется (в отличие от toggle-кнопки,
     * чей EXTRA_RECORDING зависит от текущего isRecording).
     */
    private val openAppPendingIntent: PendingIntent by lazy {
        PendingIntent.getActivity(
            this,
            REQ_OPEN_APP,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Настройки: живая подписка — переключение тумблеров во время тренировки
        // применяется сразу. Выключение автопаузы при уже стоящей автопаузе не
        // резюмит запись само: пользователь снимет паузу кнопкой.
        scope.launch {
            settingsStorage.settings.collect { s ->
                autopauseEnabled = s.autopauseEnabled
                voiceCuesEnabled = s.voiceCuesEnabled
                voiceCueIntervalKm = s.voiceCueIntervalKm
            }
        }
        initTts()
    }

    /**
     * Асинхронная инициализация TTS. Русский голос может отсутствовать —
     * тогда подсказки молча отключаются (лог, не краш): фича деградирует,
     * запись тренировки не страдает.
     */
    private fun initTts() {
        tts = android.speech.tts.TextToSpeech(this) { status ->
            if (status != android.speech.tts.TextToSpeech.SUCCESS) {
                Log.w(TAG, "TTS init failed: status=$status, voice cues disabled")
                return@TextToSpeech
            }
            val langResult = tts?.setLanguage(java.util.Locale("ru"))
            if (langResult == android.speech.tts.TextToSpeech.LANG_MISSING_DATA ||
                langResult == android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                Log.w(TAG, "Russian TTS voice unavailable, voice cues disabled")
                return@TextToSpeech
            }
            tts?.setAudioAttributes(ttsAudioAttributes)
            // Аудиофокус отпускается по окончании фразы — музыка возвращает громкость.
            tts?.setOnUtteranceProgressListener(
                object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) = abandonAudioFocus()
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) = abandonAudioFocus()
                    override fun onError(utteranceId: String?, errorCode: Int) = abandonAudioFocus()
                }
            )
            ttsReady = true
        }
    }

    /** Речевые атрибуты: навигационная подсказка поверх музыки (duck, не пауза). */
    private val ttsAudioAttributes: android.media.AudioAttributes by lazy {
        android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }

    /**
     * Произносит фразу с запросом transient-аудиофокуса (MAY_DUCK):
     * фоновая музыка приглушается, не останавливаясь. До готовности TTS
     * или при выключенных подсказках — no-op.
     */
    private fun speak(phrase: String) {
        if (!ttsReady || !voiceCuesEnabled) return
        requestAudioFocus()
        // Громкость фраз = системная громкость медиа (STREAM_MUSIC): ей управляет
        // слайдер «Громкость» в настройках и кнопки громкости устройства.
        // KEY_PARAM_VOLUME не используем — вторая независимая ручка запутывает.
        tts?.speak(
            phrase,
            android.speech.tts.TextToSpeech.QUEUE_ADD,
            null,
            "cue-${System.currentTimeMillis()}",
        )
    }

    private fun requestAudioFocus() {
        val am = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
        val request = audioFocusRequest ?: android.media.AudioFocusRequest
            .Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(ttsAudioAttributes)
            .build()
            .also { audioFocusRequest = it }
        am.requestAudioFocus(request)
    }

    private fun abandonAudioFocus() {
        val request = audioFocusRequest ?: return
        (getSystemService(AUDIO_SERVICE) as android.media.AudioManager)
            .abandonAudioFocusRequest(request)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        recoveryPrefs = getSharedPreferences(LocationConfig.PREFS_RECOVERY, MODE_PRIVATE)

        // ── Запоздалое обновление профиля после старта сервиса ───────────────────
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

        // Обновление trainingId на серверный UUID после офлайн-старта.
        // Отправляется из ViewModel когда startTraining() вернул serverUUID позже старта сервиса.
        if (intent?.hasExtra(EXTRA_TRAINING_ID_UPDATE) == true) {
            val newId = intent.getStringExtra(EXTRA_TRAINING_ID_UPDATE)
            if (!newId.isNullOrBlank()) {
                // Обновляем trainingId независимо от состояния activeTracker.
                // Если трекер ещё не инициализирован (сервис свежий или zombie-state после
                // пересоздания ОС), recoveryPrefs всё равно нужно обновить — сервис прочитает
                // новый id при следующей инициализации. Удаление условия activeTracker != null
                // устраняет тихую потерю обновления при задержке инициализации трекера.
                scope.launch {
                    bufferMutex.withLock {
                        // Сбрасываем буфер под старым id, затем переключаем под мьютексом.
                        // Без этого точки в памяти запишутся в Room под serverUUID, хотя
                        // rekeyTrainingId в ViewModel ещё не запускался → сироты в Room.
                        if (activeTracker != null) flushBufferLocked()
                        trainingId = newId  // переключаем id; новые точки пойдут под serverUUID
                    }
                    // KEY_IS_REGISTERED: этот Intent приходит ТОЛЬКО после успешного
                    // startTraining → тренировка гарантированно есть на сервере.
                    // При crash-recovery ViewModel по этому флагу выберет путь финиша
                    // (прямой saveTraining vs офлайн-цепочка с регистрацией).
                    recoveryPrefs.edit()
                        .putString(LocationConfig.KEY_ACTIVE_TRAINING, newId)
                        .putBoolean(LocationConfig.KEY_IS_REGISTERED, true)
                        .apply()
                }
            }
            return START_STICKY
        }

        // Переход из discovery-режима в тренировку без перезапуска сервиса.
        // ViewModel отправляет этот Intent вместо stop()+startForegroundService():
        // onStartCommand() вызывается на живом экземпляре → startForeground() мгновенно,
        // нет цикла destroy/create и задержки уведомления при первой тренировке.
        if (intent?.hasExtra(EXTRA_TRANSITION_TO_WORKOUT) == true) {
            // 1. Останавливаем discovery-трекер и задачи
            activeTracker?.stopTracking()
            activeTracker = null
            hintJob?.cancel()
            flushTimerJob?.cancel()
            syncJob?.cancel()

            // 2. Читаем параметры новой тренировки
            val newId = intent.getStringExtra(EXTRA_TRAINING_ID) ?: run {
                Log.e(TAG, "EXTRA_TRANSITION_TO_WORKOUT без trainingId — останавливаемся")
                stopSelf()
                return START_NOT_STICKY
            }
            val intervalMs   = intent.getLongExtra(EXTRA_INTERVAL_MS, LocationConfig.INTERVAL_MS_RUNNING)
            val newAccuracy  = intent.getFloatExtra(EXTRA_ACCURACY_THRESHOLD, LocationConfig.MAX_ACCURACY_RUNNING)

            // 3. Очищаем буфер discovery-точек перед сменой id.
            // Discovery-точки намеренно удаляются ViewModel через deletePointsForTraining(discId),
            // поэтому делаем clear(), а не flush() — иначе они запишутся в Room под newId.
            // runBlocking безопасен здесь: stopTracking() выше уже остановил GPS-коллбэки,
            // мьютекс свободен, clear() — субмиллисекундная операция.
            @Suppress("BlockingMethodInNonBlockingContext")
            runBlocking { bufferMutex.withLock { pointBuffer.clear() } }

            // 4. Обновляем режим и параметры
            trainingId        = newId
            isDiscovery       = false
            accuracyThreshold = newAccuracy
            isRecording       = true

            // 5. Сбрасываем GPS-фильтры (разрыв между discovery-точками и первой точкой
            //    тренировки не должен считаться дистанцией). firstFixDone НЕ сбрасываем:
            //    GPS уже найден в discovery — hint-таймер не нужен, пользователь не ждёт.
            lastAcceptedLocation = null
            smoothingWindow.clear()
            prevCaloriePoint  = null

            // 5a. Инициализация chronometer-базы и счётчика точек для notification.
            sessionStartedAt    = System.currentTimeMillis()
            pausedAccumulatedMs = 0L
            lastSpeedMps        = null
            recordedPointCount  = 0
            pausedByAuto        = false
            autopauseDetector.reset()
            accumulatedDistanceM = 0.0
            prevDistancePoint    = null
            milestoneTracker.reset()

            // 6. Crash-recovery и finishSyncFlow
            recoveryPrefs.edit().putString(LocationConfig.KEY_ACTIVE_TRAINING, newId).apply()
            persistSessionState()
            persistSessionProfile(intent, intervalMs)
            _finishSyncFlow.resetReplayCache()

            // 7. Показываем уведомление немедленно — это главная цель перехода
            createNotificationChannel()
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    LocationConfig.NOTIFICATION_ID,
                    notification,
                    foregroundServiceTypes(),
                )
            } else {
                startForeground(LocationConfig.NOTIFICATION_ID, notification)
            }

            // 8. WakeLock (переиспользуем или создаём новый)
            wakeLock?.let { if (it.isHeld) it.release() }
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "SmartTracker:LocationTracking",
            ).also {
                // Без ref-counting повторный acquire в flush-таймере продлевает
                // таймаут вместо инкремента счётчика (тренировки дольше 2 часов).
                it.setReferenceCounted(false)
                it.acquire(LocationConfig.WAKELOCK_TIMEOUT_MS)
            }

            // 9. Запускаем новый трекер, таймеры и sync-цикл
            startLocationUpdates(intervalMs)
            startFlushTimer()
            startSyncLoop()
            startHintTimer()

            // 9a. Автоподключение к сохранённому пульсометру
            connectHrmIfConfigured()

            // 10. Инициализируем расчёт калорий
            val typeActivId = intent.getIntExtra(EXTRA_TYPE_ACTIV_ID, -1)
            val weightKg    = if (intent.hasExtra(EXTRA_WEIGHT_KG)) intent.getFloatExtra(EXTRA_WEIGHT_KG, 0f) else null
            val heightCm    = if (intent.hasExtra(EXTRA_HEIGHT_CM)) intent.getFloatExtra(EXTRA_HEIGHT_CM, 0f) else null
            val ageYears    = intent.getIntExtra(EXTRA_AGE_YEARS, 0)
            val isMale      = intent.getBooleanExtra(EXTRA_IS_MALE, true)
            sessionWeightKg = weightKg
            sessionAgeYears = ageYears
            if (weightKg != null && heightCm != null && typeActivId >= 0) {
                scope.launch {
                    sessionCF   = CalorieCalculator.computeCF(
                        weightKg, heightCm, ageYears,
                        if (isMale) Gender.MALE else Gender.FEMALE,
                    )
                    metActivity = workoutRepository.getMETActivity(typeActivId).getOrNull()
                    Log.d(TAG, "CalorieCalc transition: CF=$sessionCF, met=$metActivity")
                }
            }

            Log.d(TAG, "Transition discovery→workout: trainingId=$newId, interval=${intervalMs}ms")
            return START_STICKY
        }

        // Команда переключения записи: применяем только если сервис уже инициализирован.
        // Если trainingId пустой (сервис убит ОС и перезапущен START_STICKY),
        // команда EXTRA_RECORDING пришла «в пустой» сервис — останавливаемся,
        // чтобы не оставить его в неконсистентном состоянии без трекера/уведомления.
        if (intent?.hasExtra(EXTRA_RECORDING) == true) {
            if (trainingId.isNotBlank() && activeTracker != null) {
                val newRecording = intent.getBooleanExtra(EXTRA_RECORDING, true)
                // Ручная команда (кнопка UI или notification): byAuto = false —
                // сбрасывает pausedByAuto, ручную паузу автопауза резюмить не будет.
                applyRecordingChange(newRecording, byAuto = false)
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

        // Основная ветка может прийти на ЖИВОЙ экземпляр (Android переиспользует сервис:
        // например, discovery-старт из ViewModel при уже работающем восстановленном сервисе).
        // Без остановки старый трекер продолжил бы колбэки параллельно с новым —
        // двойные onLocationReceived → дубли точек и утечка трекера.
        activeTracker?.stopTracking()
        activeTracker = null
        hintJob?.cancel()
        flushTimerJob?.cancel()
        syncJob?.cancel()

        trainingId  = id
        isDiscovery = intent?.getBooleanExtra(EXTRA_IS_DISCOVERY, false) ?: false
        // START_STICKY-рестарт (intent == null): параметры трекинга восстанавливаются
        // из recovery-префов — иначе велотренировка продолжилась бы с беговым
        // интервалом/порогом точности.
        accuracyThreshold = intent?.getFloatExtra(
            EXTRA_ACCURACY_THRESHOLD, LocationConfig.MAX_ACCURACY_RUNNING
        ) ?: recoveryPrefs.getFloat(
            LocationConfig.KEY_ACCURACY_THRESHOLD, LocationConfig.MAX_ACCURACY_RUNNING
        )
        val intervalMs = intent?.getLongExtra(EXTRA_INTERVAL_MS, LocationConfig.INTERVAL_MS_RUNNING)
            ?: recoveryPrefs.getLong(LocationConfig.KEY_INTERVAL_MS, LocationConfig.INTERVAL_MS_RUNNING)

        // Сбрасываем кеш replay finishSyncFlow: новая сессия — старый сигнал завершения
        // предыдущей тренировки не должен быть принят ViewModel текущей тренировки.
        _finishSyncFlow.resetReplayCache()

        if (!isDiscovery) {
            // Инициализация chronometer-состояния. Если перезапуск через START_STICKY
            // (intent == null) — восстанавливаем из prefs, чтобы chronometer продолжил
            // с реального elapsed, а recordedPointCount продолжил с реального числа точек.
            if (intent != null) {
                sessionStartedAt    = System.currentTimeMillis()
                pausedAccumulatedMs = 0L
                lastSpeedMps        = null
                recordedPointCount  = 0
                isRecording         = true
                pausedByAuto        = false
                autopauseDetector.reset()
                accumulatedDistanceM = 0.0
                prevDistancePoint    = null
                milestoneTracker.reset()
                persistSessionProfile(intent, intervalMs)
            } else {
                sessionStartedAt = recoveryPrefs.getLong(
                    LocationConfig.KEY_SESSION_STARTED_AT, System.currentTimeMillis()
                )
                pausedAccumulatedMs = recoveryPrefs.getLong(
                    LocationConfig.KEY_PAUSED_ACCUMULATED_MS, 0L
                )
                recordedPointCount = recoveryPrefs.getInt(
                    LocationConfig.KEY_RECORDED_POINT_COUNT, 0
                )
                // Тренировка могла быть убита на паузе — не возобновляем запись сами.
                // Без восстановления флага рестарт молча продолжал запись, а chronometer
                // включал паузу в elapsed (sessionStartedAt оставался временем старого resume).
                isRecording = recoveryPrefs.getBoolean(LocationConfig.KEY_IS_RECORDING, true)
                // Автопауза переживает рестарт: если пауза была автоматической,
                // движение после восстановления снимет её авто-резюмом.
                pausedByAuto = recoveryPrefs.getBoolean(LocationConfig.KEY_PAUSED_BY_AUTO, false)
                // Голосовые подсказки: восстановление дистанции и последнего
                // объявленного рубежа — иначе объявления начались бы с нуля.
                accumulatedDistanceM = recoveryPrefs
                    .getFloat(LocationConfig.KEY_ACCUM_DISTANCE_M, 0f).toDouble()
                milestoneTracker.restore(
                    lastAnnouncedKm = recoveryPrefs.getInt(LocationConfig.KEY_LAST_ANNOUNCED_KM, 0),
                    lastMilestoneElapsedMs = recoveryPrefs
                        .getLong(LocationConfig.KEY_LAST_MILESTONE_ELAPSED_MS, 0L),
                )
                // Разрыв через рестарт дистанцией не считается.
                prevDistancePoint = null
            }
            recoveryPrefs.edit().putString(LocationConfig.KEY_ACTIVE_TRAINING, trainingId).apply()
            persistSessionState()
        }

        if (!isDiscovery) {
            createNotificationChannel()
            val notification = buildNotification()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    LocationConfig.NOTIFICATION_ID,
                    notification,
                    foregroundServiceTypes(),
                )
            } else {
                startForeground(LocationConfig.NOTIFICATION_ID, notification)
            }

            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "SmartTracker:LocationTracking",
            ).also {
                // Без ref-counting повторный acquire в flush-таймере продлевает
                // таймаут вместо инкремента счётчика (тренировки дольше 2 часов).
                it.setReferenceCounted(false)
                it.acquire(LocationConfig.WAKELOCK_TIMEOUT_MS)
            }
        }

        Log.d(TAG, "onStartCommand: trainingId=$trainingId, interval=${intervalMs}ms")

        startLocationUpdates(intervalMs)
        startFlushTimer()
        startSyncLoop()
        if (!isDiscovery) startHintTimer()

        // Автоподключение к сохранённому пульсометру: обычный старт тренировки
        // и START_STICKY-рестарт (intent == null). В discovery не подключаемся —
        // пульс нужен только на записи.
        if (!isDiscovery) connectHrmIfConfigured()

        // ── Инициализация расчёта калорий (MET-метод) ────────────────────────────
        // Обычный старт: значения профиля приходят Intent-экстрами из ViewModel.
        // START_STICKY-рестарт (intent == null): восстанавливаем из recovery-префов —
        // иначе calories = null до конца тренировки после OOM-kill.
        // Если weight или height отсутствуют — calories будет null для всех точек.
        val typeActivId: Int
        val weightKg: Float?
        val heightCm: Float?
        val ageYears: Int
        val isMale: Boolean
        if (intent != null) {
            typeActivId = intent.getIntExtra(EXTRA_TYPE_ACTIV_ID, -1)
            weightKg    = if (intent.hasExtra(EXTRA_WEIGHT_KG)) intent.getFloatExtra(EXTRA_WEIGHT_KG, 0f) else null
            heightCm    = if (intent.hasExtra(EXTRA_HEIGHT_CM)) intent.getFloatExtra(EXTRA_HEIGHT_CM, 0f) else null
            ageYears    = intent.getIntExtra(EXTRA_AGE_YEARS, 0)
            isMale      = intent.getBooleanExtra(EXTRA_IS_MALE, true)
        } else {
            typeActivId = recoveryPrefs.getInt(LocationConfig.KEY_TYPE_ACTIV_ID, -1)
            weightKg    = if (recoveryPrefs.contains(LocationConfig.KEY_WEIGHT_KG))
                              recoveryPrefs.getFloat(LocationConfig.KEY_WEIGHT_KG, 0f) else null
            heightCm    = if (recoveryPrefs.contains(LocationConfig.KEY_HEIGHT_CM))
                              recoveryPrefs.getFloat(LocationConfig.KEY_HEIGHT_CM, 0f) else null
            ageYears    = recoveryPrefs.getInt(LocationConfig.KEY_AGE_YEARS, 0)
            isMale      = recoveryPrefs.getBoolean(LocationConfig.KEY_IS_MALE, true)
        }

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
     * Автоподключение к сохранённому пульсометру (активный из списка
     * в SettingsStorage; см. AppSettings.autoConnectAddress). Без датчиков
     * или без разрешения BLUETOOTH_CONNECT — no-op. Дальше HrmManager сам
     * удерживает соединение (бесконечный реконнект).
     */
    private fun connectHrmIfConfigured() {
        if (!hasBluetoothConnectPermission()) return
        scope.launch {
            val address = settingsStorage.settings.first().autoConnectAddress() ?: return@launch
            hrmManager.connect(address)
        }
    }

    /**
     * Битовая маска типов для startForeground (API 29+).
     *
     * connectedDevice добавляется ТОЛЬКО при выданном BLUETOOTH_CONNECT:
     * на API 34+ startForeground с типом без выполненных prerequisites
     * (нужного runtime-разрешения) кидает SecurityException — условие
     * обязательно, не косметика.
     */
    private fun foregroundServiceTypes(): Int {
        var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        if (hasBluetoothConnectPermission()) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        }
        return types
    }

    /** API 31+: runtime BLUETOOTH_CONNECT; ниже — legacy BLUETOOTH выдан при установке. */
    private fun hasBluetoothConnectPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
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
                if (!isDiscovery && !isShuttingDown) {
                    // Heartbeat даже при пустом буфере (пауза): flushBufferLocked
                    // персистит только при фактической записи точек, а ViewModel
                    // по KEY_LAST_PERSIST_AT отличает живую сессию от протухшей.
                    persistSessionState()
                    // Продление WakeLock: одиночный acquire(2ч) истекал на тренировках
                    // длиннее — на API 26–28 CPU затроттливался при выключенном экране.
                    // setReferenceCounted(false) → повторный acquire сбрасывает таймаут,
                    // при краше сервиса лок всё равно отпустится максимум через 2 часа.
                    wakeLock?.acquire(LocationConfig.WAKELOCK_TIMEOUT_MS)
                }
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
        // Discovery-режим: sync с сервером отключён. Discovery-trainingId не зарегистрирован
        // на сервере → uploadGpsPoints() вернул бы 404 на каждом батче.
        if (isDiscovery) return
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
                val hint = NotificationCompat.Builder(this@LocationTrackingService, LocationConfig.CHANNEL_ID)
                    .setContentTitle("SmartTracker")
                    .setContentText("Поиск GPS... Выйдите на открытое место")
                    .setSmallIcon(R.drawable.ic_activity_running)
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .build()
                notificationManager.notify(LocationConfig.NOTIFICATION_ID, hint)
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

        // ── Автопауза: детектор питается точками ДО слоя 4 ───────────────────────
        // Слой 4 отбрасывает стоячие точки (dist < antijitter) — именно они и
        // сигнализируют остановку; после него детектор никогда не увидел бы паузу.
        // Скорость: sensor-speed, при её отсутствии — дистанция/время от prev
        // (accuracy-дрейф уже отсечён слоями 1 и 3).
        if (!isDiscovery && autopauseEnabled && trainingId.isNotBlank() &&
            System.currentTimeMillis() - sessionStartedAt >= LocationConfig.AUTOPAUSE_WARMUP_MS
        ) {
            val detectorSpeedMps: Float? = when {
                location.hasSpeed() -> location.speed
                prev != null -> {
                    val dtSec = (location.time - prev.time) / 1000.0
                    if (dtSec > 0) (prev.distanceTo(location) / dtSec).toFloat() else null
                }
                else -> null
            }
            when (autopauseDetector.onPoint(detectorSpeedMps, location.time, isRecording)) {
                AutopauseDetector.Event.PAUSE -> {
                    Log.d(TAG, "Autopause: stillness detected, pausing")
                    applyRecordingChange(newRecording = false, byAuto = true)
                }
                AutopauseDetector.Event.RESUME ->
                    // Ручную паузу авто-резюм не трогает — только свою.
                    if (pausedByAuto) {
                        Log.d(TAG, "Autopause: movement detected, resuming")
                        applyRecordingChange(newRecording = true, byAuto = true)
                    }
                AutopauseDetector.Event.NONE -> Unit
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
            // Восстанавливаем стандартное уведомление вместо hint'а "Выйдите на открытое место".
            // rebuildNotification сам проверяет isDiscovery — в discovery ничего не делает.
            rebuildNotification()
            offlineMapManager.downloadRegionIfNeeded(
                LatLng(smoothed.latitude, smoothed.longitude),
                isWifiConnected(),
            )
        }

        lastAcceptedLocation = smoothed
        // Обновляем скорость для отображения в notification (м/с; конверсия в км/ч в buildNotification).
        // Запись даже если isRecording=false ниже — чтобы в момент resume последняя
        // известная скорость отрисовалась без задержки.
        if (smoothed.hasSpeed()) {
            lastSpeedMps = smoothed.speed
        }

        // ── Запись в буфер только во время активного трекинга ───────────────────
        // При isRecording = false GPS-трекер продолжает работать: фильтры, сглаживание
        // и lastAcceptedLocation обновляются выше — это нужно чтобы не было «прыжков»
        // после снятия паузы. Точки в Room не попадают.
        if (!isRecording) return

        // Discovery-режим: точки пишутся в Room (startGpsStatusObserver в ViewModel
        // наблюдает за ними и обновляет gpsStatus → ACQUIRED / UNAVAILABLE корректно),
        // но НЕ синхронизируются с сервером — syncLoop отключён для discovery
        // (startSyncLoop завершается сразу при isDiscovery == true), а в onDestroy
        // syncUnsentPoints() также пропускается.

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
        // Пульс — последний свежий сэмпл с датчика; null если датчик не подключён
        // или сэмпл протух (freshBpm не отдаёт устаревшее значение после обрыва).
        val heartRate = if (isDiscovery) null else hrmManager.freshBpm()
        val point = rawPoint.copy(calories = calories, heartRate = heartRate)
        Log.d(TAG, "point: calories=${point.calories}, speed=${point.speed}")
        // Обновляем опорную точку ПОСЛЕ copy, чтобы использовать уже финальный объект.
        prevCaloriePoint = point

        // Считаем точку записанной синхронно (до scope.launch): буфер гарантированно
        // флашится в Room, поэтому recordedPointCount == будущему числу точек в Room.
        // Только для тренировки — discovery-точки пишутся под другим trainingId.
        // Инкремент на Main-looper'е (callback GPS) — без гонки с чтением в onStartCommand.
        if (!isDiscovery) {
            recordedPointCount++

            // ── Дистанция сервиса + километровые голосовые подсказки ─────────
            // prevDistancePoint = null после resume/рестарта — телепорт через
            // паузу дистанцией не считается (симметрично gap-парам ViewModel).
            prevDistancePoint?.let {
                accumulatedDistanceM += statsUseCase.distanceBetween(it, point)
            }
            prevDistancePoint = point
            // Трекер двигается независимо от тумблера подсказок: включение
            // мид-тренировки продолжит объявления со следующего рубежа,
            // а не выдаст пачку пропущенных.
            val elapsedMs = if (sessionStartedAt > 0L)
                System.currentTimeMillis() - sessionStartedAt else 0L
            milestoneTracker.onDistance(accumulatedDistanceM, elapsedMs, voiceCueIntervalKm)
                ?.let { cue ->
                    speak(TtsPhraseFormatter.kilometerCue(cue.km, cue.lapPaceMsPerKm))
                    // Рубеж — редкое событие; персист сразу, чтобы crash-recovery
                    // не повторил объявление.
                    persistSessionState()
                }
        }

        scope.launch {
            bufferMutex.withLock {
                pointBuffer.add(point)
                if (pointBuffer.size >= LocationConfig.BUFFER_FLUSH_SIZE) {
                    flushBufferLocked()
                }
            }
        }

        // Обновляем notification: новая скорость должна отразиться на lock screen.
        // Частота — раз в ~3 сек (INTERVAL_MS_RUNNING), что приемлемо для UX и батареи.
        rebuildNotification()
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
        // Персистим состояние после фактической записи точек в Room — чтобы при
        // перезапуске сервиса через START_STICKY gap-индекс паузы был верным.
        // При shutdown не персистим: onDestroy уже очищает recoveryPrefs, повторная
        // запись из финального flush оставила бы «мусорное» chronometer-состояние.
        if (!isDiscovery && !isShuttingDown) persistSessionState()
    }

    override fun onDestroy() {
        super.onDestroy()
        isShuttingDown = true
        activeTracker?.stopTracking()
        activeTracker = null
        hintJob?.cancel()
        flushTimerJob?.cancel()
        syncJob?.cancel()

        // TTS: остановить текущую фразу и освободить engine + аудиофокус.
        ttsReady = false
        tts?.stop()
        tts?.shutdown()
        tts = null
        abandonAudioFocus()

        // Пульсометр: разорвать соединение и остановить реконнекты — тренировка
        // завершена, держать связь незачем (менеджер singleton, но соединение
        // живёт только пока оно кому-то нужно).
        hrmManager.disconnect()

        // Финальный сброс буфера + sync или очистка discovery-точек.
        // Захватываем значения полей до scope.launch: onStartCommand мог уже переключить
        // isDiscovery на false (Android переиспользует сервис при stop+start), поэтому
        // читаем флаги синхронно до старта корутины.
        val wasDiscovery = isDiscovery
        val serviceTrainingId = trainingId
        scope.launch {
            try {
                bufferMutex.withLock { flushBufferLocked() }
                if (!wasDiscovery) {
                    syncUnsentPoints()
                } else {
                    // Discovery-режим: syncLoop был отключён (discovery-UUID не зарегистрирован
                    // на сервере), но точки накопились в Room для gpsStatus-наблюдателя.
                    // Удаляем их: они временные, хранить смысла нет — в противном случае
                    // они остаются с isSent=false навсегда и при следующем starте сервиса
                    // (если isDiscovery случайно сбросится) могут уйти на сервер как 404.
                    locationRepository.deletePointsForTraining(serviceTrainingId)
                }
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

        // Очищаем crash-recovery целиком — тренировка завершена штатно.
        // clear() вместо перечисления ключей: покрывает и профиль калорий,
        // и gap-индексы, и heartbeat — забытый ключ не протечёт в следующую сессию.
        if (::recoveryPrefs.isInitialized) {
            recoveryPrefs.edit().clear().apply()
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
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Собирает rich-уведомление активной тренировки.
     *
     * Содержимое:
     * - Заголовок "Тренировка идёт"
     * - Chronometer (live HH:MM:SS) от `sessionStartedAt`. В режиме паузы chronometer
     *   выключен, выводится статичный текст "На паузе".
     * - Текущая скорость в км/ч (или "Скорость: —" если ещё неизвестна).
     * - Action-кнопка Пауза/Продолжить (зависит от `isRecording`).
     * - Тап на тело → открывает MainActivity (singleTop сохраняет текущий стек).
     *
     * `setOnlyAlertOnce(true)` — обновления не вибрируют/не звенят повторно.
     * `setUsesChronometer` — Android System UI сам тикает таймер раз в секунду
     * без необходимости вызывать `notify()` для обновления времени.
     */
    /**
     * Единый путь смены состояния записи — и для ручных команд (EXTRA_RECORDING),
     * и для автопаузы. Идемпотентен: повторная команда с тем же значением
     * (двойной тап по кнопке notification) не ломает счётчик pausedAccumulatedMs.
     *
     * @param byAuto true = инициатор автопауза; только такая пауза может быть
     *   снята авто-резюмом. Ручная команда всегда сбрасывает [pausedByAuto].
     */
    private fun applyRecordingChange(newRecording: Boolean, byAuto: Boolean) {
        if (newRecording == isRecording) {
            // Состояние не меняется, но ручная команда «Пауза» поверх уже стоящей
            // автопаузы переводит паузу в ручную: авто-резюм отключается, снять
            // её сможет только пользователь.
            if (!byAuto) pausedByAuto = false
            return
        }
        if (newRecording) {
            // Resume: сдвигаем sessionStartedAt в прошлое на накопленный elapsed,
            // чтобы chronometer продолжил с того же значения.
            sessionStartedAt = System.currentTimeMillis() - pausedAccumulatedMs
            // Сбрасываем предыдущую точку — пауза не должна попасть в интервал калорий.
            prevCaloriePoint = null
            // Аналогично для дистанции подсказок: телепорт через паузу не считается.
            prevDistancePoint = null
        } else {
            // Pause: фиксируем суммарный elapsed как разницу между now и виртуальной
            // базой sessionStartedAt. Использовать `=`, не `+=`: после прошлого
            // resume sessionStartedAt уже сдвинут в прошлое на накопленный elapsed,
            // поэтому (now - sessionStartedAt) — это полный суммарный elapsed,
            // а не дельта новой активной фазы.
            if (sessionStartedAt > 0L) {
                pausedAccumulatedMs = System.currentTimeMillis() - sessionStartedAt
            }
            // Персистим gap-индекс паузы: при crash-recovery ViewModel восстановит
            // pauseGapIndices — без них дистанция после рестарта посчитала бы
            // «телепорт» через паузу как реальное движение.
            if (!isDiscovery) {
                val existing = recoveryPrefs.getString(
                    LocationConfig.KEY_PAUSE_GAP_INDICES, ""
                ).orEmpty()
                val updated = if (existing.isBlank()) "$recordedPointCount"
                              else "$existing,$recordedPointCount"
                recoveryPrefs.edit()
                    .putString(LocationConfig.KEY_PAUSE_GAP_INDICES, updated)
                    .apply()
            }
        }
        isRecording = newRecording
        pausedByAuto = !newRecording && byAuto
        // Смена состояния любым инициатором сбрасывает распознавание — серия
        // «стоячих»/«движущихся» точек прошлого состояния не должна утекать в новое.
        autopauseDetector.reset()

        persistSessionState()

        // Сообщаем ViewModel о смене состояния — синхронизирует UI когда
        // pause/resume инициирован из notification или автопаузой, и передаёт
        // точный gap-индекс (recordedPointCount) на момент паузы.
        if (!_recordingStateFlow.tryEmit(RecordingState(newRecording, recordedPointCount))) {
            // Потеря pause-события = потеря gap-индекса → live-дистанция посчитает
            // «телепорт» через паузу. VM восстановит gap'ы из recovery-prefs только
            // при пересоздании — залогировать, чтобы увидеть в поле.
            Log.w(TAG, "recordingStateFlow buffer overflow, event dropped (recording=$newRecording, gap=$recordedPointCount)")
        }

        // Голосовое подтверждение автопаузы: экран погашен, телефон в кармане —
        // без звука пользователь не узнает, что трекер остановился/продолжил.
        if (byAuto) {
            speak(if (newRecording) TtsPhraseFormatter.RESUME_CUE else TtsPhraseFormatter.AUTOPAUSE_CUE)
        }

        rebuildNotification()
    }

    private fun buildNotification(): Notification {
        val isPaused = !isRecording

        // Текст под заголовком: "На паузе (HH:MM:SS)" или "Скорость: 9.8 км/ч".
        // На паузе chronometer выключен (Android System UI не тикает) — поэтому
        // вшиваем сюда статичный elapsed на момент паузы для контекста.
        val contentText: String = when {
            // Автопауза помечается отдельно: пользователь должен понимать, что
            // трекер остановился сам и сам продолжит при движении.
            isPaused && pausedByAuto ->
                getString(R.string.notif_autopaused, formatHhMmSs(pausedAccumulatedMs))
            isPaused -> getString(R.string.notif_paused, formatHhMmSs(pausedAccumulatedMs))
            lastSpeedMps != null -> getString(
                R.string.notif_speed_kmh_format,
                (lastSpeedMps ?: 0f) * 3.6f,
            )
            else -> getString(R.string.notif_speed_unknown)
        }

        // PendingIntent кнопки Pause/Resume. EXTRA_RECORDING инвертирует текущее состояние:
        // если запись идёт — кнопка ставит на паузу (false), иначе возобновляет (true).
        val toggleIntent = Intent(this, LocationTrackingService::class.java).apply {
            putExtra(EXTRA_RECORDING, isPaused)   // !isRecording → новое состояние
        }
        val togglePending = PendingIntent.getService(
            this,
            REQ_TOGGLE_RECORDING,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val toggleIcon = if (isPaused) R.drawable.ic_notif_play else R.drawable.ic_notif_pause
        val toggleLabel = getString(
            if (isPaused) R.string.notif_action_resume else R.string.notif_action_pause
        )

        val titleRes = if (isPaused) R.string.notif_workout_title_paused
                       else R.string.notif_workout_title
        val builder = NotificationCompat.Builder(this, LocationConfig.CHANNEL_ID)
            .setContentTitle(getString(titleRes))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_activity_running)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppPendingIntent)
            .addAction(toggleIcon, toggleLabel, togglePending)

        // Chronometer: показываем только во время активной записи.
        // На паузе: фриз — chronometer выключен, текст "На паузе" уже в contentText.
        if (!isPaused && sessionStartedAt > 0L) {
            builder.setUsesChronometer(true)
                .setWhen(sessionStartedAt)
                .setShowWhen(true)
        } else {
            builder.setUsesChronometer(false)
                .setShowWhen(false)
        }

        return builder.build()
    }

    /**
     * Перерисовывает уведомление на основе текущего состояния полей сервиса.
     * Вызывать при: смене isRecording (pause/resume), новой принятой GPS-точке (новая скорость),
     * первом GPS-fix (исчезновение hint'а).
     *
     * Защищено от вызова в discovery-режиме — там foreground notification не создаётся,
     * вызов `notify()` после `stopForeground` бесполезен (мог бы создать orphan-уведомление).
     */
    private fun rebuildNotification() {
        if (isDiscovery || trainingId.isBlank()) return
        notificationManager.notify(LocationConfig.NOTIFICATION_ID, buildNotification())
    }

    /**
     * Сохраняет chronometer-состояние тренировки (момент старта, накопленная пауза,
     * число точек) в recoveryPrefs — для восстановления при перезапуске сервиса
     * через START_STICKY после убийства процесса ОС.
     */
    private fun persistSessionState() {
        if (!::recoveryPrefs.isInitialized) return
        recoveryPrefs.edit()
            .putLong(LocationConfig.KEY_SESSION_STARTED_AT, sessionStartedAt)
            .putLong(LocationConfig.KEY_PAUSED_ACCUMULATED_MS, pausedAccumulatedMs)
            .putInt(LocationConfig.KEY_RECORDED_POINT_COUNT, recordedPointCount)
            .putBoolean(LocationConfig.KEY_IS_RECORDING, isRecording)
            .putBoolean(LocationConfig.KEY_PAUSED_BY_AUTO, pausedByAuto)
            .putFloat(LocationConfig.KEY_ACCUM_DISTANCE_M, accumulatedDistanceM.toFloat())
            .putInt(LocationConfig.KEY_LAST_ANNOUNCED_KM, milestoneTracker.lastAnnouncedKm)
            .putLong(
                LocationConfig.KEY_LAST_MILESTONE_ELAPSED_MS,
                milestoneTracker.lastMilestoneElapsedMs,
            )
            // Heartbeat: ViewModel по нему отличает живую сессию (сервис работает
            // или вот-вот перезапустится START_STICKY) от протухшей (force-stop,
            // рестарт не придёт) — см. readRecoverableSession.
            .putLong(LocationConfig.KEY_LAST_PERSIST_AT, System.currentTimeMillis())
            .apply()
    }

    /**
     * Персистит профиль калорий и параметры трекинга при старте сессии.
     * Восстанавливаются при START_STICKY-рестарте (intent == null): без них рестарт
     * терял CF/MET (calories=null) и откатывал интервал/точность на профиль бега.
     * Заодно сбрасывает gap-индексы и флаг регистрации предыдущей сессии.
     */
    private fun persistSessionProfile(intent: Intent, intervalMs: Long) {
        if (!::recoveryPrefs.isInitialized) return
        val editor = recoveryPrefs.edit()
            .putInt(LocationConfig.KEY_TYPE_ACTIV_ID, intent.getIntExtra(EXTRA_TYPE_ACTIV_ID, -1))
            .putInt(LocationConfig.KEY_AGE_YEARS, intent.getIntExtra(EXTRA_AGE_YEARS, 0))
            .putBoolean(LocationConfig.KEY_IS_MALE, intent.getBooleanExtra(EXTRA_IS_MALE, true))
            .putLong(LocationConfig.KEY_INTERVAL_MS, intervalMs)
            .putFloat(LocationConfig.KEY_ACCURACY_THRESHOLD, accuracyThreshold)
            .putLong(LocationConfig.KEY_TRAINING_STARTED_AT, sessionStartedAt)
            .putBoolean(LocationConfig.KEY_IS_REGISTERED, false)
            .remove(LocationConfig.KEY_PAUSE_GAP_INDICES)
        if (intent.hasExtra(EXTRA_WEIGHT_KG)) {
            editor.putFloat(LocationConfig.KEY_WEIGHT_KG, intent.getFloatExtra(EXTRA_WEIGHT_KG, 0f))
        } else {
            editor.remove(LocationConfig.KEY_WEIGHT_KG)
        }
        if (intent.hasExtra(EXTRA_HEIGHT_CM)) {
            editor.putFloat(LocationConfig.KEY_HEIGHT_CM, intent.getFloatExtra(EXTRA_HEIGHT_CM, 0f))
        } else {
            editor.remove(LocationConfig.KEY_HEIGHT_CM)
        }
        editor.apply()
    }

    companion object {
        private const val TAG = "LocationTrackingService"

        // ── Request codes для PendingIntent уведомления ───────────────────────────
        // Стабильные значения нужны чтобы FLAG_UPDATE_CURRENT обновлял extras в
        // существующем PendingIntent, а не создавал новый. Каждой кнопке/тапу — свой код.
        private const val REQ_OPEN_APP         = 100
        private const val REQ_TOGGLE_RECORDING = 101

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
         * При true: точки пишутся в Room (нужно для gpsStatus-наблюдателя в ViewModel),
         * но НЕ синхронизируются с сервером (syncLoop и финальный syncUnsentPoints отключены).
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
         * Поток уведомления о смене состояния записи.
         * Эмитится только при фактической смене — двойные команды с одинаковым значением
         * не порождают событий (см. ветку EXTRA_RECORDING).
         *
         * Используется чтобы синхронизировать UI-state ViewModel когда pause/resume
         * приходит из notification (а не из UI-кнопки), и чтобы передать точный
         * gap-индекс паузы (recordedPointCount).
         *
         * replay=0: подписчики получают только живые изменения. Это важно — VM не должна
         * реагировать на «древний» стейт из прошлой сессии тренировки.
         *
         * extraBufferCapacity=16: при буфере 1 быстрый pause→resume (флаппинг
         * автопаузы) с занятым коллектором молча ронял событие в tryEmit —
         * потерянный gap-индекс паузы навсегда впечатывал «телепорт» в
         * live-дистанцию (аккумулятор VM не пересчитывается задним числом).
         */
        private val _recordingStateFlow = MutableSharedFlow<RecordingState>(
            replay = 0,
            extraBufferCapacity = 16,
        )
        val recordingStateFlow: SharedFlow<RecordingState> = _recordingStateFlow.asSharedFlow()

        /**
         * Команда обновления trainingId в работающем сервисе.
         * Отправляется из ViewModel после получения serverUUID при офлайн-старте.
         * Сервис переключает внутренний trainingId и обновляет crash-recovery prefs.
         */
        const val EXTRA_TRAINING_ID_UPDATE = "extra_training_id_update"

        /**
         * Переход из discovery-режима в тренировку без перезапуска сервиса.
         * ViewModel отправляет этот Intent вместо stop()+startForegroundService().
         * onStartCommand() вызывается на живом экземпляре → startForeground() мгновенно,
         * нет задержки уведомления при первой тренировке.
         */
        const val EXTRA_TRANSITION_TO_WORKOUT = "extra_transition_to_workout"

        /**
         * Отправляет Intent с флагом записи в уже запущенный сервис.
         * Сервис обрабатывает его в [onStartCommand] без повторной инициализации.
         */
        fun setRecording(context: android.content.Context, recording: Boolean) {
            val intent = Intent(context, LocationTrackingService::class.java)
                .putExtra(EXTRA_RECORDING, recording)
            context.startService(intent)
        }

        /**
         * Снимок восстанавливаемой сессии тренировки из recovery-префов.
         * Читается ViewModel при инициализации (см. [readRecoverableSession]).
         */
        data class RecoverableSession(
            val trainingId: String,
            val isRecording: Boolean,
            /** Виртуальная база chronometer: elapsed = now − sessionStartedAt (при записи). */
            val sessionStartedAt: Long,
            /** Суммарный elapsed на момент паузы (актуален при isRecording == false). */
            val pausedAccumulatedMs: Long,
            /** Момент первого нажатия «Начать» — для даты на экране итогов. */
            val trainingStartedAt: Long,
            /** Gap-индексы пауз — без них дистанция посчитает телепорт через паузу. */
            val pauseGapIndices: List<Int>,
            /** type_activ_id выбранной активности; -1 если не был известен. */
            val typeActivId: Int,
            /** true = startTraining подтверждён сервером → финиш прямым saveTraining. */
            val isRegisteredOnServer: Boolean,
        )

        /**
         * Читает восстанавливаемую сессию тренировки после смерти процесса.
         *
         * Возвращает null (и подчищает префы), если сессии нет или она протухла:
         * heartbeat ([LocationConfig.KEY_LAST_PERSIST_AT]) обновляется сервисом каждые
         * ~5 сек; если он старше [LocationConfig.RECOVERY_STALE_MS] — сервис мёртв и
         * START_STICKY-рестарт не пришёл (force-stop, перезагрузка). Восстанавливать
         * «фантомную» сессию с пустым многочасовым таймером хуже, чем начать заново.
         *
         * Вызывается из WorkoutStartViewModel ДО запуска discovery-GPS: discovery-intent
         * на живой восстановленный сервис перезаписал бы его trainingId и молча убил
         * запись восстановленной тренировки.
         */
        fun readRecoverableSession(context: android.content.Context): RecoverableSession? {
            val prefs = context.getSharedPreferences(
                LocationConfig.PREFS_RECOVERY, android.content.Context.MODE_PRIVATE
            )
            val trainingId = prefs.getString(LocationConfig.KEY_ACTIVE_TRAINING, null)
                ?: return null
            val lastPersistAt = prefs.getLong(LocationConfig.KEY_LAST_PERSIST_AT, 0L)
            if (System.currentTimeMillis() - lastPersistAt > LocationConfig.RECOVERY_STALE_MS) {
                // Протухшая сессия: точки остаются в Room, открытая тренировка на сервере
                // закроется через finishOrphanedAndRetryStart при следующем старте.
                prefs.edit().clear().apply()
                return null
            }
            val gaps = prefs.getString(LocationConfig.KEY_PAUSE_GAP_INDICES, "").orEmpty()
                .split(',')
                .mapNotNull { it.trim().toIntOrNull() }
            return RecoverableSession(
                trainingId           = trainingId,
                isRecording          = prefs.getBoolean(LocationConfig.KEY_IS_RECORDING, true),
                sessionStartedAt     = prefs.getLong(
                    LocationConfig.KEY_SESSION_STARTED_AT, System.currentTimeMillis()
                ),
                pausedAccumulatedMs  = prefs.getLong(LocationConfig.KEY_PAUSED_ACCUMULATED_MS, 0L),
                trainingStartedAt    = prefs.getLong(
                    LocationConfig.KEY_TRAINING_STARTED_AT,
                    prefs.getLong(LocationConfig.KEY_SESSION_STARTED_AT, System.currentTimeMillis()),
                ),
                pauseGapIndices      = gaps,
                typeActivId          = prefs.getInt(LocationConfig.KEY_TYPE_ACTIV_ID, -1),
                isRegisteredOnServer = prefs.getBoolean(LocationConfig.KEY_IS_REGISTERED, false),
            )
        }
    }
}
