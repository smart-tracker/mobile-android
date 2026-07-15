package com.example.smarttracker.presentation.workout.start

import android.app.Application
import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.example.smarttracker.data.hrm.HrmManager
import com.example.smarttracker.data.hrm.model.HrmConnectionState
import com.example.smarttracker.data.hrm.model.HrmSample
import com.example.smarttracker.data.local.AppSettings
import com.example.smarttracker.data.local.SavedHrmDevice
import com.example.smarttracker.data.local.SettingsStorage
import com.example.smarttracker.data.location.LocationConfig
import com.example.smarttracker.data.location.LocationTrackingService
import com.example.smarttracker.data.location.OfflineMapManager
import com.example.smarttracker.data.work.OfflineFinishScheduler
import com.example.smarttracker.domain.model.ActiveTrainingResult
import com.example.smarttracker.domain.model.NetworkUnavailableException
import com.example.smarttracker.domain.model.WorkoutType
import com.example.smarttracker.domain.repository.AuthRepository
import com.example.smarttracker.domain.repository.LocationRepository
import com.example.smarttracker.domain.repository.WorkoutRepository
import com.example.smarttracker.domain.usecase.CalculateTrainingStatsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Unit-тесты WorkoutStartViewModel.
 *
 * Приоритет — crash-recovery контракт сервис↔ViewModel (CLAUDE.md, нюанс 28):
 *  - живая recovery-сессия реаттачится БЕЗ запуска discovery-GPS
 *    (discovery-intent на живой сервис перезаписал бы trainingId и убил запись);
 *  - pauseGapIndices кладутся в state ДО observeTrackingData;
 *  - протухший heartbeat (RECOVERY_STALE_MS) → сессия сброшена, префы очищены;
 *  - elapsed восстанавливается: на паузе — pausedAccumulatedMs,
 *    при записи — now − sessionStartedAt.
 *
 * Плюс жизненный цикл тренировки:
 *  - re-key localUUID → serverUUID при успешном startTraining;
 *  - офлайн-старт (NetworkUnavailableException) — тренировка продолжается локально;
 *  - onFinishClick незарегистрированной тренировки → savePendingFinish + enqueue;
 *  - onFinishClick зарегистрированной → saveTraining после таймаута finishSyncFlow;
 *  - освобождение trackPoints при финише (нюанс 21);
 *  - блокировка смены типа во время тренировки;
 *  - форматтеры formatPace/formatDuration.
 *
 * Robolectric даёт настоящие Context/SharedPreferences: recovery-префы пишутся
 * в тесте теми же ключами, какими их пишет сервис — контракт проверяется честно.
 * application = Application::class — не запускаем SmartTrackerApp.onCreate()
 * (MapLibre требует нативные .so, которых нет в JVM).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class WorkoutStartViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private lateinit var context: Context
    private lateinit var workoutRepository: WorkoutRepository
    private lateinit var locationRepository: LocationRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var offlineMapManager: OfflineMapManager
    private lateinit var offlineFinishScheduler: OfflineFinishScheduler

    // Последний созданный ViewModel — scope гасится в конце КАЖДОГО теста
    // (см. runVmTest). Без этого таймер тренировки (бесконечный
    // while(isActive){delay(1000)}) не даёт test-scheduler'у стать idle →
    // runTest busy-spin'ит в фазе очистки (advanceUntilIdle крутит виртуальное
    // время вечно). Гасить в @After поздно: cleanup runTest выполняется раньше.
    private var lastVm: WorkoutStartViewModel? = null

    private val runningType = WorkoutType(id = 1, name = "Бег", iconKey = "1")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        context = ApplicationProvider.getApplicationContext()
        // Чистые recovery-префы перед каждым тестом — Robolectric переиспользует контекст
        recoveryPrefs().edit().clear().commit()
    }

    @After
    fun tearDown() {
        lastVm = null
        Dispatchers.resetMain()
    }

    /**
     * runTest с гарантированной отменой viewModelScope в конце ТЕЛА теста —
     * до фазы очистки runTest, которая иначе вечно прокручивает
     * самоперепланирующийся таймер тренировки.
     */
    private fun runVmTest(
        block: suspend kotlinx.coroutines.test.TestScope.() -> Unit,
    ) = runTest {
        try {
            block()
        } finally {
            lastVm?.viewModelScope?.cancel()
        }
    }

    private fun recoveryPrefs() =
        context.getSharedPreferences(LocationConfig.PREFS_RECOVERY, Context.MODE_PRIVATE)

    /**
     * Пишет recovery-префы теми же ключами, какими их пишет LocationTrackingService
     * (persistRecoveryState). Дефолт — живая сессия на паузе (isRecording=false):
     * без запущенного таймера тест не зависит от реальных часов.
     */
    private fun writeRecoverySession(
        trainingId: String = "recovered-id",
        isRecording: Boolean = false,
        sessionStartedAt: Long = System.currentTimeMillis() - 60_000L,
        pausedAccumulatedMs: Long = 45_000L,
        lastPersistAt: Long = System.currentTimeMillis(),
        pauseGapIndices: String = "3,7",
        typeActivId: Int = 1,
        isRegistered: Boolean = true,
    ) {
        recoveryPrefs().edit()
            .putString(LocationConfig.KEY_ACTIVE_TRAINING, trainingId)
            .putBoolean(LocationConfig.KEY_IS_RECORDING, isRecording)
            .putLong(LocationConfig.KEY_SESSION_STARTED_AT, sessionStartedAt)
            .putLong(LocationConfig.KEY_PAUSED_ACCUMULATED_MS, pausedAccumulatedMs)
            .putLong(LocationConfig.KEY_TRAINING_STARTED_AT, sessionStartedAt)
            .putLong(LocationConfig.KEY_LAST_PERSIST_AT, lastPersistAt)
            .putString(LocationConfig.KEY_PAUSE_GAP_INDICES, pauseGapIndices)
            .putInt(LocationConfig.KEY_TYPE_ACTIV_ID, typeActivId)
            .putBoolean(LocationConfig.KEY_IS_REGISTERED, isRegistered)
            .commit()
    }

    // Флоу состояния пульсометра — поля, чтобы тесты могли двигать значения
    // после создания ViewModel (мок HrmManager отдаёт их как StateFlow).
    private val hrmConnectionState = MutableStateFlow(HrmConnectionState.DISCONNECTED)
    private val hrmSample = MutableStateFlow<HrmSample?>(null)
    private lateinit var hrmManager: HrmManager

    private fun createViewModel(
        types: List<WorkoutType> = listOf(runningType),
        settings: AppSettings = AppSettings(),
    ): WorkoutStartViewModel {
        workoutRepository = mock {
            on { workoutTypesFlow() } doReturn flowOf(types)
        }
        locationRepository = mock {
            on { observePointsForTraining(any()) } doReturn flowOf(emptyList())
            onBlocking { getLastKnownPoint() } doReturn null
        }
        authRepository = mock {
            onBlocking { getUserInfo() } doReturn Result.failure(Exception("profile unavailable"))
        }
        offlineMapManager = mock()
        offlineFinishScheduler = mock()
        val settingsStorage: SettingsStorage = mock {
            on { this.settings } doReturn flowOf(settings)
        }
        hrmConnectionState.value = HrmConnectionState.DISCONNECTED
        hrmSample.value = null
        hrmManager = mock {
            on { connectionState } doReturn hrmConnectionState
            on { currentSample } doReturn hrmSample
        }
        return WorkoutStartViewModel(
            workoutRepository,
            locationRepository,
            CalculateTrainingStatsUseCase(),   // чистый Kotlin — реальный экземпляр
            offlineMapManager,
            authRepository,
            offlineFinishScheduler,
            settingsStorage,
            hrmManager,
            context,
        ).also { lastVm = it }
    }

    /** Все интенты, отправленные в сервисы с момента старта теста. */
    private fun startedServiceIntents(): List<android.content.Intent> {
        val shadow = shadowOf(context as Application)
        return generateSequence { shadow.nextStartedService }.toList()
    }

    // ── Crash-recovery (нюанс 28) ─────────────────────────────────────────────

    @Test
    fun `init без recovery-сессии - запускается discovery GPS`() = runVmTest {
        createViewModel()

        val discovery = startedServiceIntents().find {
            it.getBooleanExtra(LocationTrackingService.EXTRA_IS_DISCOVERY, false)
        }
        assertNotNull("Ожидался discovery-intent к LocationTrackingService", discovery)
    }

    @Test
    fun `живая recovery-сессия - реаттач БЕЗ discovery`() = runVmTest {
        writeRecoverySession()

        val vm = createViewModel()

        // Контракт: discovery-intent на живой восстановленный сервис перезаписал бы
        // его trainingId и молча убил запись — интентов быть не должно вовсе.
        val discovery = startedServiceIntents().find {
            it.getBooleanExtra(LocationTrackingService.EXTRA_IS_DISCOVERY, false)
        }
        assertNull("Discovery не должен стартовать при живой recovery-сессии", discovery)

        val s = vm.state.value
        assertTrue(s.isWorkoutStarted)
        assertFalse("Сессия была на паузе", s.isTracking)
        assertEquals(45_000L, s.elapsedMs)
        assertEquals("00:00:45", s.timerDisplay)
        // pauseGapIndices восстановлены ДО observeTrackingData — иначе дистанция
        // посчитает телепорт через паузу
        assertEquals(listOf(3, 7), s.pauseGapIndices)
        // Статистика пересчитывается из Room по восстановленному id
        verify(locationRepository).observePointsForTraining("recovered-id")
    }

    @Test
    fun `recovery с isRecording=true - таймер продолжается от sessionStartedAt`() = runVmTest {
        writeRecoverySession(
            isRecording = true,
            sessionStartedAt = System.currentTimeMillis() - 60_000L,
        )

        val vm = createViewModel()

        val s = vm.state.value
        assertTrue(s.isTracking)
        assertTrue(
            "elapsed должен восстановиться ≈60 сек, получено ${s.elapsedMs}",
            s.elapsedMs in 55_000..70_000,
        )
    }

    @Test
    fun `протухший heartbeat - сессия сброшена, префы очищены, discovery стартует`() = runVmTest {
        writeRecoverySession(
            lastPersistAt = System.currentTimeMillis() - LocationConfig.RECOVERY_STALE_MS - 1_000L,
        )

        val vm = createViewModel()

        assertFalse(vm.state.value.isWorkoutStarted)
        assertNull(
            "Протухшие префы должны быть очищены",
            recoveryPrefs().getString(LocationConfig.KEY_ACTIVE_TRAINING, null),
        )
        val discovery = startedServiceIntents().find {
            it.getBooleanExtra(LocationTrackingService.EXTRA_IS_DISCOVERY, false)
        }
        assertNotNull("После сброса протухшей сессии должен стартовать discovery", discovery)
    }

    // ── Старт тренировки ──────────────────────────────────────────────────────

    @Test
    fun `onStartWorkoutClick - успех, re-key localUUID на serverUUID`() = runVmTest {
        val vm = createViewModel()
        workoutRepository.stub {
            onBlocking { startTraining(eq(1), anyOrNull()) } doReturn
                Result.success(ActiveTrainingResult("server-id", 1, "2026-07-06T10:00:00+00:00", "ok"))
        }

        vm.onStartWorkoutClick()

        val s = vm.state.value
        assertTrue(s.isTracking)
        assertTrue(s.isWorkoutStarted)
        assertFalse(s.isStarting)
        // Room-точки перевешаны с localUUID на serverUUID, наблюдатель перезапущен
        verifyBlocking(locationRepository) { rekeyTrainingId(any(), eq("server-id")) }
        verify(locationRepository).observePointsForTraining("server-id")
    }

    @Test
    fun `onStartWorkoutClick - нет сети, тренировка продолжается локально`() = runVmTest {
        val vm = createViewModel()
        workoutRepository.stub {
            onBlocking { startTraining(eq(1), anyOrNull()) } doReturn
                Result.failure(NetworkUnavailableException())
        }

        vm.onStartWorkoutClick()

        val s = vm.state.value
        assertTrue("Трекинг не должен останавливаться при офлайн-старте", s.isTracking)
        assertFalse(s.isStarting)
        assertNotNull(s.errorMessage)
        verifyBlocking(locationRepository, never()) { rekeyTrainingId(any(), any()) }
    }

    @Test
    fun `onPauseClick замораживает трекинг`() = runVmTest {
        val vm = createViewModel()
        workoutRepository.stub {
            onBlocking { startTraining(eq(1), anyOrNull()) } doReturn
                Result.failure(NetworkUnavailableException())
        }
        vm.onStartWorkoutClick()

        vm.onPauseClick()

        assertFalse(vm.state.value.isTracking)
    }

    // ── Завершение тренировки ─────────────────────────────────────────────────

    @Test
    fun `onFinishClick незарегистрированной - офлайн-цепочка WorkManager`() = runVmTest {
        val vm = createViewModel()
        workoutRepository.stub {
            onBlocking { startTraining(eq(1), anyOrNull()) } doReturn
                Result.failure(NetworkUnavailableException())
        }
        vm.onStartWorkoutClick()

        vm.onFinishClick()

        // Прямой saveTraining вернул бы 404 — сервер не знает localUUID.
        verifyBlocking(workoutRepository, never()) { saveTraining(any(), any(), anyOrNull(), anyOrNull()) }
        // Очередь: typeActivId обязателен — SyncGpsPointsWorker сначала зарегистрирует тренировку
        verifyBlocking(workoutRepository) {
            savePendingFinish(any(), any(), anyOrNull(), anyOrNull(), eq(1), anyOrNull())
        }
        verify(offlineFinishScheduler).enqueue(any())

        val s = vm.state.value
        assertNotNull("Оверлей итогов должен показаться", s.summaryOverlay)
        // Нюанс 21: live-точки освобождены, оверлей держит их в снимке
        assertTrue(s.trackPoints.isEmpty())
        assertFalse(s.isWorkoutStarted)
    }

    @Test
    fun `onFinishClick зарегистрированной - saveTraining после таймаута sync-сигнала`() = runVmTest {
        val vm = createViewModel()
        workoutRepository.stub {
            onBlocking { startTraining(eq(1), anyOrNull()) } doReturn
                Result.success(ActiveTrainingResult("server-id", 1, "2026-07-06T10:00:00+00:00", "ok"))
            onBlocking { saveTraining(any(), any(), anyOrNull(), anyOrNull()) } doReturn
                Result.failure(Exception("500"))   // не-сетевая ошибка → без очереди
        }
        vm.onStartWorkoutClick()

        vm.onFinishClick()
        // finishSyncFlow молчит (сервис не запущен в тесте) → ждём таймаут 5 сек
        advanceTimeBy(5_001)

        verifyBlocking(workoutRepository) {
            saveTraining(eq("server-id"), any(), anyOrNull(), anyOrNull())
        }
        // Не-сетевая ошибка — retry бессмысленен, офлайн-очередь не используется
        verifyBlocking(workoutRepository, never()) {
            savePendingFinish(any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
        }
    }

    // ── Выбор типа активности ─────────────────────────────────────────────────

    @Test
    fun `смена типа заблокирована во время тренировки`() = runVmTest {
        val cycling = WorkoutType(id = 3, name = "Вело", iconKey = "3")
        val vm = createViewModel(types = listOf(runningType, cycling))
        workoutRepository.stub {
            onBlocking { startTraining(any(), anyOrNull()) } doReturn
                Result.failure(NetworkUnavailableException())
        }
        vm.onStartWorkoutClick()

        vm.onQuickTypeSelected(cycling)
        vm.onSheetTypeSelected(cycling)

        assertEquals("Тип не должен меняться во время тренировки",
            runningType.id, vm.state.value.selectedType?.id)
    }

    // ── Пульсометр (BLE HRM) ──────────────────────────────────────────────────

    @Test
    fun `hrmConfigured true при сохранённом датчике`() = runVmTest {
        // Один VM на тест: runVmTest гасит scope только последнего созданного
        val vm = createViewModel(
            settings = AppSettings(
                hrmDevices = listOf(SavedHrmDevice("AA:BB:CC:DD:EE:FF", "Polar H10")),
                hrmActiveAddress = "AA:BB:CC:DD:EE:FF",
            ),
        )
        assertTrue("Непустой список датчиков → hrmConfigured", vm.state.value.hrmConfigured)
    }

    @Test
    fun `hrmConfigured false без сохранённого датчика`() = runVmTest {
        val vm = createViewModel()
        assertFalse("Без адреса → hrmConfigured=false", vm.state.value.hrmConfigured)
    }

    @Test
    fun `активный датчик автоподключается при входе на экран`() = runVmTest {
        // Robolectric sdk=28 → ветка «ниже S»: legacy BLUETOOTH выдан при установке
        createViewModel(
            settings = AppSettings(
                hrmDevices = listOf(
                    SavedHrmDevice("11:22:33:44:55:66", "Magene H64"),
                    SavedHrmDevice("AA:BB:CC:DD:EE:FF", "Polar H10"),
                ),
                hrmActiveAddress = "AA:BB:CC:DD:EE:FF",
            ),
        )
        verify(hrmManager).connect("AA:BB:CC:DD:EE:FF")
    }

    @Test
    fun `без активного - автоподключение к первому из списка`() = runVmTest {
        createViewModel(
            settings = AppSettings(
                hrmDevices = listOf(SavedHrmDevice("11:22:33:44:55:66", "Magene H64")),
                hrmActiveAddress = null,
            ),
        )
        verify(hrmManager).connect("11:22:33:44:55:66")
    }

    @Test
    fun `без сохранённого датчика автоподключения нет`() = runVmTest {
        createViewModel()
        verify(hrmManager, never()).connect(any())
    }

    @Test
    fun `heartRateDisplay показывает bpm только при CONNECTED`() = runVmTest {
        val vm = createViewModel()

        // Сэмпл есть, но соединения нет — прочерк
        hrmSample.value = HrmSample(bpm = 148, timestampMs = System.currentTimeMillis())
        assertEquals("--", vm.state.value.heartRateDisplay)
        assertFalse(vm.state.value.hrmConnected)

        // Подключились — живой пульс
        hrmConnectionState.value = HrmConnectionState.CONNECTED
        assertEquals("148", vm.state.value.heartRateDisplay)
        assertTrue(vm.state.value.hrmConnected)

        // Обрыв (RECONNECTING) — снова прочерк
        hrmConnectionState.value = HrmConnectionState.RECONNECTING
        assertEquals("--", vm.state.value.heartRateDisplay)
        assertFalse(vm.state.value.hrmConnected)
    }

    @Test
    fun `showHistorySummary считает avg и max пульс по точкам с пульсом`() = runVmTest {
        val vm = createViewModel()
        val points = listOf(
            historyPoint(0, heartRate = 120),
            historyPoint(1, heartRate = null),   // обрыв датчика — не участвует
            historyPoint(2, heartRate = 160),
        )
        workoutRepository.stub {
            onBlocking { getTrainingDetail("hist-1") } doReturn Result.success(points)
        }

        vm.showHistorySummary(historyItem("hist-1"), "Бег")

        val overlay = vm.state.value.summaryOverlay
        assertNotNull(overlay)
        assertEquals("140 уд/мин", overlay!!.avgHeartRateDisplay)
        assertEquals("160 уд/мин", overlay.maxHeartRateDisplay)
    }

    @Test
    fun `showHistorySummary без пульса в точках - поля null, секция скрыта`() = runVmTest {
        val vm = createViewModel()
        val points = listOf(historyPoint(0, heartRate = null), historyPoint(1, heartRate = null))
        workoutRepository.stub {
            onBlocking { getTrainingDetail("hist-2") } doReturn Result.success(points)
        }

        vm.showHistorySummary(historyItem("hist-2"), "Бег")

        val overlay = vm.state.value.summaryOverlay
        assertNotNull(overlay)
        assertNull("История до BR-16 — пульса нет", overlay!!.avgHeartRateDisplay)
        assertNull(overlay.maxHeartRateDisplay)
    }

    private fun historyPoint(index: Int, heartRate: Int?) =
        com.example.smarttracker.domain.model.LocationPoint(
            trainingId   = "hist",
            timestampUtc = 1_700_000_000_000L + index * 5_000L,
            elapsedNanos = index * 5_000_000_000L,
            latitude     = 61.0 + index * 0.001,
            longitude    = 34.0,
            altitude     = null,
            speed        = null,
            accuracy     = null,
            heartRate    = heartRate,
        )

    private fun historyItem(id: String) = com.example.smarttracker.domain.model.TrainingHistoryItem(
        trainingId    = id,
        typeActivId   = 1,
        date          = java.time.LocalDate.of(2026, 7, 10),
        timeStart     = "2026-07-10T10:00:00+00:00",
        timeEnd       = "2026-07-10T10:30:00+00:00",
        kilocalories  = null,
        distanceM     = 5000.0,
        avgSpeed      = null,
        elevationGain = null,
    )

    // ── Форматтеры (companion) ────────────────────────────────────────────────

    @Test
    fun `formatPace - 3 м-с это 5-33 мин-км`() {
        assertEquals("5:33 мин/км", WorkoutStartViewModel.formatPace(3.0))
    }

    @Test
    fun `formatPace - нулевая скорость`() {
        assertEquals("00:00 мин/км", WorkoutStartViewModel.formatPace(0.0))
    }

    @Test
    fun `formatDuration - час двадцать три минуты`() {
        assertEquals("01:23:00", WorkoutStartViewModel.formatDuration((83 * 60 * 1000).toLong()))
    }
}
