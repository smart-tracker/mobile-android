package com.example.smarttracker.presentation.workout.start

import android.util.Log
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.smarttracker.data.location.LocationConfig
import com.example.smarttracker.data.location.LocationTrackingService
import com.example.smarttracker.data.work.SaveTrainingWorker
import com.example.smarttracker.data.work.SyncGpsPointsWorker
import com.example.smarttracker.data.location.OfflineMapManager
import com.example.smarttracker.domain.model.ActiveTrainingConflictException
import com.example.smarttracker.domain.model.Gender
import com.example.smarttracker.domain.model.NetworkUnavailableException
import com.example.smarttracker.domain.model.LocationPoint
import com.example.smarttracker.domain.model.User
import com.example.smarttracker.domain.model.WorkoutType
import com.example.smarttracker.domain.repository.AuthRepository
import com.example.smarttracker.domain.repository.LocationRepository
import com.example.smarttracker.domain.repository.WorkoutRepository
import com.example.smarttracker.domain.usecase.CalculateTrainingStatsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import androidx.core.content.edit

/**
 * ViewModel экрана начала / активной тренировки.
 *
 * Загружает список типов тренировок при инициализации.
 * Управляет жизненным циклом LocationTrackingService, таймером тренировки и
 * статистикой (дистанция, темп, калории), пересчитываемой из GPS-точек Room.
 *
 * GPS-статус (SEARCHING / ACQUIRED / UNAVAILABLE) отслеживается через наблюдение
 * за LocationRepository: первая точка → ACQUIRED, 30 сек без точек → UNAVAILABLE.
 */
@HiltViewModel
class WorkoutStartViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val locationRepository: LocationRepository,
    private val calculateTrainingStatsUseCase: CalculateTrainingStatsUseCase,
    private val offlineMapManager: OfflineMapManager,
    private val authRepository: AuthRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    /** Статус получения GPS-фикса. Используется для overlay на экране. */
    enum class GpsStatus { SEARCHING, ACQUIRED, UNAVAILABLE }

    data class UiState(
        /** Текущая дата в формате "DD.MM.YYYY (День недели)" */
        val currentDate: String = "",
        /** Полный список типов тренировок с сервера */
        val workoutTypes: List<WorkoutType> = emptyList(),
        /**
         * Три типа, отображаемых в строке быстрого выбора.
         * При выборе тип встаёт на первое место, остальные сдвигаются вправо.
         */
        val pinnedTypes: List<WorkoutType> = emptyList(),
        /** Выбранный тип тренировки */
        val selectedType: WorkoutType? = null,
        /** true пока список типов загружается */
        val isTypesLoading: Boolean = true,
        /** true когда тренировка запущена — переключает кнопку "Начать" на "Пауза"+"Завершить" */
        val isTracking: Boolean = false,
        /**
         * true с момента первого нажатия «Начать» и до нажатия «Завершить».
         * При isWorkoutStarted = true выбор типа активности заблокирован.
         */
        val isWorkoutStarted: Boolean = false,
        /** Статус GPS: меняется после старта трекинга */
        val gpsStatus: GpsStatus = GpsStatus.SEARCHING,
        /** Накопленное время в миллисекундах (для сохранения при паузе) */
        val elapsedMs: Long = 0L,
        /** Отображаемое время тренировки "HH:MM:SS" */
        val timerDisplay: String = "00:00:00",
        /** Пройденное расстояние, например "1.23 км" */
        val distanceDisplay: String = "0.00 км",
        /** Средний темп, например "5:30 мин/км" */
        val avgSpeedDisplay: String = "00:00 мин/км",
        /** Сожжённые калории, например "86 кКал" */
        val caloriesDisplay: String = "0 кКал",
        /**
         * Пройденное расстояние в метрах — сырое числовое значение.
         * Используется при сохранении тренировки на сервере вместо парсинга [distanceDisplay].
         */
        val distanceMeters: Double = 0.0,
        /**
         * Сожжённые калории — сырое числовое значение.
         * Используется при сохранении тренировки на сервере вместо парсинга [caloriesDisplay].
         */
        val kilocalories: Double = 0.0,
        /** Сообщение об ошибке загрузки типов (null = нет ошибки) */
        val errorMessage: String? = null,
        /** GPS-точки текущей тренировки для рисования трека на карте */
        val trackPoints: List<LocationPoint> = emptyList(),
        /**
         * true когда MapLibre не смог загрузить тайлы (нет сети + нет кэша).
         * Устанавливается через callback onDidFailLoadingMap из MapViewComposable.
         * При завершении тренировки сбрасывается в false.
         */
        val mapTilesFailed: Boolean = false,
        /** true пока выполняется POST /training/start — блокирует кнопку «Начать» */
        val isStarting: Boolean = false,
        /** Множество iconKey избранных типов активностей, хранится в SharedPreferences */
        val favoriteIds: Set<String> = emptySet(),
        /** Поисковый запрос в шторке выбора активности */
        val searchQuery: String = "",
        /**
         * Последняя точка из любой предыдущей тренировки (из Room).
         * Используется для начального центрирования карты до получения GPS-сигнала.
         * null = тренировок ещё не было, карта покажет дефолтное положение.
         */
        val lastKnownLocation: LocationPoint? = null,
    ) {
        /** true когда GPS-сигнал получен */
        val isGpsActive: Boolean get() = gpsStatus == GpsStatus.ACQUIRED

        /** Типы активностей, отфильтрованные по запросу и отсортированные: избранные сверху */
        val filteredAndSortedTypes: List<WorkoutType>
            get() = workoutTypes
                .filter { searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true) }
                .sortedWith(compareByDescending { it.iconKey in favoriteIds })
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /**
     * WorkManager для постановки в очередь [SaveTrainingWorker].
     * Инициализируется лениво через getInstance — при реализации [Configuration.Provider]
     * в SmartTrackerApp WorkManager использует HiltWorkerFactory.
     */
    private val workManager = WorkManager.getInstance(context)

    /**
     * Сигнал принудительного выхода из сессии (оба токена истекли).
     * WorkoutHomeScreen наблюдает этот flow и при true вызывает onLogout().
     * Делегирует в authRepository.sessionExpiredFlow → tokenStorage.sessionExpiredFlow.
     */
    val sessionExpired: StateFlow<Boolean> = authRepository.sessionExpiredFlow

    // UUID тренировки: генерируется при первом старте, сохраняется при паузе/возобновлении,
    // сбрасывается в null при завершении (onFinishClick).
    private var currentTrainingId: String? = null

    /**
     * Количество точек в Room на момент паузы.
     * При возобновлении observeTrackingData использует max(processedCount, якорь),
     * чтобы пропустить дистанцию от последней точки до паузы до первой точки после.
     * @Volatile: пишется из Main-thread (onPauseClick), читается из Dispatchers.Default.
     */
    @Volatile private var resumeAnchorPointCount: Int = 0

    // UUID и Job discovery-фазы: GPS ищется сразу при открытии экрана,
    // до того как пользователь нажмёт «Начать тренировку».
    private var discoveryTrainingId: String? = null
    private var discoveryObserverJob: Job? = null

    // Профиль пользователя (вес/рост/возраст/пол) для расчёта калорий в LocationTrackingService
    private var userProfile: User? = null

    // Таймер — отдельный Job, считает wall-clock время с учётом паузы
    private var timerJob: Job? = null
    private var startTimeMs: Long = 0L
    private var pausedElapsedMs: Long = 0L

    // Наблюдатель за GPS-точками: обновляет GPS-статус и статистику
    private var observerJob: Job? = null

    init {
        // Устанавливаем текущую дату
        val currentDate = formatCurrentDate()
        _state.update { it.copy(currentDate = currentDate) }

        collectWorkoutTypes()
        loadUserProfile()
        val favIds = loadFavoriteIds()
        if (favIds.isNotEmpty()) _state.update { it.copy(favoriteIds = favIds) }
        // Сначала читаем последнюю сохранённую точку для начального центрирования карты.
        // Это исключает гонку, при которой discovery GPS успевает записать временную точку
        // в Room и getLastKnownPoint() возвращает не историю пользователя, а "призрачную" точку
        // текущего открытия экрана.
        viewModelScope.launch {
            val last = locationRepository.getLastKnownPoint()
            if (last != null) _state.update { it.copy(lastKnownLocation = last) }
            // GPS стартует сразу после первичного чтения — иконка по-прежнему видна без нажатия «Начать».
            startDiscoveryGps()
        }
    }

    /**
     * Загружает профиль текущего пользователя из API для расчёта калорий.
     * Если профиль недоступен (нет сети, не заполнен) — calories будет null в GPS-точках.
     *
     * Race condition fix: если getUserInfo() завершается уже после того как сервис запущен
     * (пользователь нажал «Начать» до загрузки профиля), отправляем профиль в работающий
     * сервис через EXTRA_PROFILE_UPDATE — CF будет вычислен без перезапуска трекинга.
     */
    private fun loadUserProfile() {
        viewModelScope.launch {
            authRepository.getUserInfo()
                .onSuccess { profile ->
                    userProfile = profile
                    // Если тренировка уже запущена — досылаем профиль в работающий сервис
                    currentTrainingId?.let {
                        context.startService(buildProfileUpdateIntent())
                    }
                }
                // тихо игнорируем: calories = null если профиль недоступен
        }
    }

    /**
     * Строит Intent для запоздалого обновления профиля в работающий LocationTrackingService.
     * Содержит только профильные экстры — без TRAINING_ID, INTERVAL_MS, ACCURACY_THRESHOLD.
     * Вызывается только когда userProfile уже присвоен.
     */
    private fun buildProfileUpdateIntent(): Intent {
        val profile = userProfile
        val selectedType = _state.value.selectedType
        if (selectedType == null) {
            // Без typeActivId сервис не сможет загрузить metActivity →
            // calories будут null пока пользователь не выберет тип тренировки.
            Log.w(TAG, "buildProfileUpdateIntent: selectedType == null, " +
                "EXTRA_TYPE_ACTIV_ID не отправлен → metActivity не загрузится")
        }
        return Intent(context, LocationTrackingService::class.java).apply {
            putExtra(LocationTrackingService.EXTRA_PROFILE_UPDATE, true)
            if (selectedType != null) {
                putExtra(LocationTrackingService.EXTRA_TYPE_ACTIV_ID, selectedType.id)
            }
            profile?.let { p ->
                p.weight?.let { putExtra(LocationTrackingService.EXTRA_WEIGHT_KG, it) }
                p.height?.let { putExtra(LocationTrackingService.EXTRA_HEIGHT_CM, it) }
                val ageYears = Period.between(p.birthDate, LocalDate.now()).years
                putExtra(LocationTrackingService.EXTRA_AGE_YEARS, ageYears)
                putExtra(LocationTrackingService.EXTRA_IS_MALE, p.gender == Gender.MALE)
            }
        }
    }

    private fun collectWorkoutTypes() {
        viewModelScope.launch {
            _state.update { it.copy(isTypesLoading = true) }
            workoutRepository.workoutTypesFlow()
                .catch { e ->
                    _state.update { it.copy(
                        isTypesLoading = false,
                        errorMessage = e.localizedMessage ?: "Ошибка загрузки типов",
                    ) }
                }
                .collect { types ->
                    _state.update { current ->
                        // Сохраняем выбор пользователя при фоновом обновлении списка.
                        val selected = current.selectedType
                            ?.let { s -> types.find { it.id == s.id } }
                            ?: types.firstOrNull()
                        // Первая эмиссия: берём первые 3; последующие: обновляем iconFile, порядок не трогаем.
                        val pinned = if (current.pinnedTypes.isEmpty())
                            types.take(3)
                        else
                            current.pinnedTypes.map { p -> types.find { it.id == p.id } ?: p }
                        current.copy(
                            workoutTypes   = types,
                            pinnedTypes    = pinned,
                            selectedType   = selected,
                            isTypesLoading = false,
                        )
                    }
                }
        }
    }

    /**
     * Нажатие «Начать тренировку» — регистрирует тренировку на сервере,
     * затем запускает таймер и сервис GPS-трекинга.
     *
     * При возобновлении после паузы trainingId уже существует — сервер не вызывается повторно.
     * При ошибке сети — fallback на локальный UUID с предупреждением пользователю.
     */
    fun onStartWorkoutClick() {
        val selectedType = _state.value.selectedType ?: return

        // Защита от двойного тапа: пока идёт сетевой запрос, игнорируем повторные нажатия
        if (_state.value.isStarting) return

        // Возобновление после паузы — ID уже получен, сервер не вызываем
        if (currentTrainingId != null) {
            resumeTracking()
            return
        }

        // Первый старт — регистрируем тренировку на сервере
        viewModelScope.launch {
            _state.update { it.copy(isStarting = true) }

            workoutRepository.startTraining(selectedType.id)
                .onSuccess { result ->
                    currentTrainingId = result.activeTrainingId
                    _state.update { it.copy(isStarting = false) }
                    resumeTracking()
                }
                .onFailure { error ->
                    when (error) {
                        is ActiveTrainingConflictException -> {
                            // На сервере незавершённая тренировка — автоматически завершаем её
                            // и повторяем старт без участия пользователя.
                            finishOrphanedAndRetryStart(selectedType.id)
                        }
                        else -> {
                            // Fallback: локальный UUID, тренировка записывается офлайн
                            currentTrainingId = UUID.randomUUID().toString()
                            _state.update { it.copy(
                                isStarting = false,
                                errorMessage = "Нет связи с сервером. Тренировка сохраняется локально.",
                            ) }
                            resumeTracking()
                        }
                    }
                }
        }
    }

    /**
     * Автоматически завершает «осиротевшую» тренировку на сервере и повторяет старт.
     *
     * Вызывается когда сервер вернул 400 (ActiveTrainingConflictException) — значит у
     * пользователя есть незавершённая тренировка (например, приложение крашнулось).
     * Флоу: GET /training/active → POST /training/{id}/save_training → повторный POST /training/start.
     *
     * Всё выполняется без участия пользователя — он просто видит, что тренировка стартует.
     * Если getActiveTraining или saveTraining упадут — переходим на локальный UUID (fallback).
     */
    private suspend fun finishOrphanedAndRetryStart(typeActivId: Int) {
        val activeId = workoutRepository.getActiveTraining().getOrNull()
        if (activeId == null) {
            // Не смогли получить ID осиротевшей тренировки — используем локальный UUID.
            // Ситуация маловероятна: сервер сначала сказал «есть конфликт», а потом
            // не смог вернуть ID. Скорее всего временная сетевая ошибка.
            Log.w(TAG, "finishOrphanedAndRetryStart: getActiveTraining() failed, falling back to local UUID")
            currentTrainingId = UUID.randomUUID().toString()
            _state.update { it.copy(
                isStarting = false,
                errorMessage = "Не удалось завершить предыдущую тренировку. Тренировка сохраняется локально.",
            ) }
            resumeTracking()
            return
        }

        // Завершаем осиротевшую тренировку с текущим временем.
        // Итоговую статистику не знаем — передаём null (бэкенд сохранит как есть).
        workoutRepository.saveTraining(
            trainingId          = activeId,
            timeEnd             = Instant.now()
                .atZone(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            totalDistanceMeters = null,
            totalKilocalories   = null,
        )

        // Повторяем старт новой тренировки
        workoutRepository.startTraining(typeActivId)
            .onSuccess { result ->
                currentTrainingId = result.activeTrainingId
                _state.update { it.copy(isStarting = false) }
                resumeTracking()
            }
            .onFailure {
                // Повторный старт тоже упал — уходим в офлайн
                Log.e(TAG, "finishOrphanedAndRetryStart: retry startTraining failed", it)
                currentTrainingId = UUID.randomUUID().toString()
                _state.update { it.copy(
                    isStarting = false,
                    errorMessage = "Нет связи с сервером. Тренировка сохраняется локально.",
                ) }
                resumeTracking()
            }
    }

    /**
     * Запускает таймер и GPS-сервис для текущего trainingId.
     * Вызывается как при первом старте (после startTraining), так и при возобновлении из паузы.
     */
    private fun resumeTracking() {
        val trainingId = currentTrainingId ?: return
        val isFirstStart = _state.value.isWorkoutStarted.not()

        if (isFirstStart) {
            discoveryObserverJob?.cancel()
            discoveryObserverJob = null
            stopLocationService()
            // Удаляем discovery-точки из Room: они временные и нужны были только для
            // gpsStatus-наблюдателя. Выполняем до startLocationService, чтобы не было
            // гонки: Android может переиспользовать тот же Service-инстанс (stop+start),
            // тогда isDiscovery уже false к onDestroy и удаление из сервиса не сработает.
            // Захватываем id до обнуления поля.
            val discId = discoveryTrainingId
            if (discId != null) {
                viewModelScope.launch {
                    locationRepository.deletePointsForTraining(discId)
                }
            }
            discoveryTrainingId = null
        }

        startTimeMs = System.currentTimeMillis() - pausedElapsedMs
        timerJob = viewModelScope.launch {
            while (isActive) {
                val elapsed = System.currentTimeMillis() - startTimeMs
                _state.update { it.copy(
                    elapsedMs    = elapsed,
                    timerDisplay = formatDuration(elapsed),
                ) }
                delay(1000)
            }
        }

        _state.update { it.copy(
            isTracking       = true,
            isWorkoutStarted = true,
            // При первом старте сохраняем ACQUIRED если GPS уже был пойман в discovery-режиме —
            // физически сигнал не пропадал, просто сменился trainingId.
            // SEARCHING ставим только если GPS ещё не был получен.
            gpsStatus        = if (isFirstStart && it.gpsStatus != GpsStatus.ACQUIRED)
                                   GpsStatus.SEARCHING
                               else
                                   it.gpsStatus,
        ) }

        if (isFirstStart) {
            startLocationService(trainingId)
            observeTrackingData(trainingId)
        } else {
            // Возобновление после паузы: разрешаем запись точек снова
            LocationTrackingService.setRecording(context, true)
        }
    }

    /** Нажатие «Пауза» — замораживает таймер; GPS продолжает работать, запись в Room останавливается */
    fun onPauseClick() {
        pausedElapsedMs = _state.value.elapsedMs
        timerJob?.cancel()
        _state.update { it.copy(isTracking = false) }
        // +1: calculateDeltaDistance начинает с (fromIndex-1), поэтому якорь на размер списка
        // дал бы пару (последняя точка до паузы → первая точка после паузы) — это и есть gap.
        // С +1 startIdx == size, пара gap'а не попадает в расчёт.
        resumeAnchorPointCount = _state.value.trackPoints.size + 1
        // GPS-трекер продолжает работать (сервис жив), но точки в Room не пишутся
        LocationTrackingService.setRecording(context, false)
    }

    /**
     * Нажатие «Завершить» — останавливает трекинг, отправляет итоги на сервер
     * и сбрасывает статистику в ноль.
     *
     * saveTraining выполняется fire-and-forget: при ошибке данные остаются в Room,
     * пользователь не блокируется.
     */
    fun onFinishClick() {
        val trainingId = currentTrainingId
        val state = _state.value

        timerJob?.cancel()
        stopLocationService()
        observerJob?.cancel()

        // Отправить итоги на сервер (fire-and-forget с офлайн-fallback)
        if (trainingId != null) {
            // Фиксируем timeEnd один раз — используется и в сетевом запросе,
            // и в очереди, чтобы оба варианта содержали одинаковое время завершения.
            val timeEnd = Instant.now()
                .atZone(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            val distanceMeters = state.distanceMeters.takeIf { it > 0 }
            val kilocalories   = state.kilocalories.takeIf { it > 0 }

            viewModelScope.launch {
                // Ждём сигнала от сервиса о завершении финального flush+sync.
                // Таймаут 5 сек — если сервис упал или сигнал не пришёл, идём дальше
                // (худший случай: saveTraining закроет тренировку без последних точек,
                // но пользователь не блокируется).
                val syncSignaled = withTimeoutOrNull(5_000L) {
                    LocationTrackingService.finishSyncFlow.first()
                }
                if (syncSignaled == null) {
                    Log.w(TAG, "Timeout waiting for service sync signal; calling saveTraining anyway")
                }

                workoutRepository.saveTraining(
                    trainingId          = trainingId,
                    timeEnd             = timeEnd,
                    totalDistanceMeters = distanceMeters,
                    totalKilocalories   = kilocalories,
                ).onFailure { e ->
                    when (e) {
                        is NetworkUnavailableException -> {
                            // Сеть недоступна — сохраняем в очередь и ставим цепочку WorkManager.
                            // NonCancellable: запись в Room не прерывается если ViewModel
                            // уничтожается прямо в этот момент (пользователь закрыл приложение).
                            Log.w(TAG, "saveTraining failed (no network), queuing for later delivery")
                            withContext(NonCancellable) {
                                workoutRepository.savePendingFinish(
                                    trainingId          = trainingId,
                                    timeEnd             = timeEnd,
                                    totalDistanceMeters = distanceMeters,
                                    totalKilocalories   = kilocalories,
                                )
                            }
                            enqueueOfflineFinishWork(trainingId)
                        }
                        else -> {
                            // HTTP 4xx/5xx или прочие ошибки — не ставим в очередь,
                            // retry бессмысленен или ситуация не связана с сетью.
                            Log.e(TAG, "saveTraining failed with non-retryable error", e)
                        }
                    }
                }
            }
        }

        pausedElapsedMs = 0L
        _state.update { it.copy(
            isTracking       = false,
            isWorkoutStarted = false,
            // Сохраняем ACQUIRED если GPS был активен — физически сигнал не пропал,
            // только завершилась тренировка и сменится trainingId на новый discovery.
            // SEARCHING ставим только если GPS и так не был пойман.
            // Симметрично тому, что делается при старте тренировки в resumeTracking().
            gpsStatus        = if (it.gpsStatus == GpsStatus.ACQUIRED)
                                   GpsStatus.ACQUIRED
                               else
                                   GpsStatus.SEARCHING,
            elapsedMs        = 0L,
            timerDisplay     = "00:00:00",
            distanceDisplay  = "0.00 км",
            avgSpeedDisplay  = "00:00 мин/км",
            caloriesDisplay  = "0 кКал",
            distanceMeters   = 0.0,
            kilocalories     = 0.0,
            trackPoints      = emptyList(),
            mapTilesFailed   = false,
        ) }
        currentTrainingId = null
        offlineMapManager.reset()
        // Сразу перезапускаем discovery-GPS: иконка остаётся живой между тренировками
        startDiscoveryGps()
    }

    /** Карта сообщила, что тайлы недоступны (нет сети + нет кэша). Показываем fallback. */
    fun onMapTilesFailed() {
        _state.update { it.copy(mapTilesFailed = true) }
    }

    /** Клик по иконке в быстром ряду — только меняет selectedType */
    fun onQuickTypeSelected(type: WorkoutType) {
        if (_state.value.isWorkoutStarted) return // тип заблокирован во время тренировки
        _state.update { it.copy(selectedType = type) }
    }

    /** Переключает избранность типа активности и сохраняет в SharedPreferences */
    fun onToggleFavorite(typeActivId: String) {
        val current = _state.value.favoriteIds
        val updated = if (typeActivId in current) current - typeActivId else current + typeActivId
        context.getSharedPreferences("workout_prefs", Context.MODE_PRIVATE)
            .edit {
                putString("favorite_activity_ids", updated.joinToString(","))
            }
        _state.update { it.copy(favoriteIds = updated) }
    }

    /** Обновляет поисковый запрос в шторке выбора активности */
    fun onSearchQueryChange(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    /** Выбор из шторки — тип встаёт на первое место */
    fun onSheetTypeSelected(type: WorkoutType) {
        if (_state.value.isWorkoutStarted) return // тип заблокирован во время тренировки
        _state.update { state ->
            val newPinned = (listOf(type) + state.pinnedTypes.filter { it.id != type.id }).take(3)
            state.copy(selectedType = type, pinnedTypes = newPinned)
        }
    }

    /**
     * Единый наблюдатель за GPS-точками текущей тренировки.
     *
     * Инкрементальный расчёт статистики: вместо полного прохода по всем точкам (O(n²))
     * на каждое событие Flow обрабатываются только новые пары точек (O(n_new)).
     * Тяжёлый расчёт выполняется на Dispatchers.Default, не блокируя UI.
     * collectLatest отменяет предыдущий блок при поступлении нового события.
     *
     * GPS-таймаут: 30 сек без новых точек → gpsStatus = UNAVAILABLE; тренировка при этом
     * продолжается (таймер и трекинг не останавливаются). Таймаут перезапускается после
     * каждой точки, поэтому потеря сигнала после ACQUIRED тоже корректно обнаруживается.
     */
    private fun observeTrackingData(trainingId: String) {
        observerJob?.cancel()
        observerJob = viewModelScope.launch {
            // Инкрементальный счётчик живёт в скоупе coroutine — синхронизация не нужна
            var accumulatedDistanceM = 0.0
            var processedCount = 0

            // Дочерний Job таймаута: перезапускается после каждой новой точки,
            // чтобы корректно обнаруживать потерю сигнала и после ACQUIRED.
            // Тренировка при потере сигнала НЕ останавливается — только обновляется
            // индикатор GPS. Таймер и трекинг продолжаются: когда сигнал вернётся,
            // observerJob увидит новые точки и gpsStatus вернётся в ACQUIRED.
            var timeoutJob: Job? = null
            fun restartTimeout() {
                timeoutJob?.cancel()
                timeoutJob = launch {
                    delay(LocationConfig.GPS_FIX_TIMEOUT_MS)
                    _state.update { it.copy(gpsStatus = GpsStatus.UNAVAILABLE) }
                }
            }
            restartTimeout()

            locationRepository.observePointsForTraining(trainingId)
                .collectLatest { points ->
                    // GPS-статус: первая точка → ACQUIRED; таймаут перезапускается на каждой точке
                    if (points.isNotEmpty()) {
                        if (_state.value.gpsStatus != GpsStatus.ACQUIRED) {
                            _state.update { it.copy(gpsStatus = GpsStatus.ACQUIRED) }
                        }
                        restartTimeout()
                    }

                    val currentKilocalories = _state.value.kilocalories

                    // Инкрементальный расчёт на фоновом потоке, чтобы не блокировать UI.
                    // Внутри withContext нет точек приостановки, поэтому collectLatest
                    // не может прервать блок посередине — accumulatedDistanceM и processedCount
                    // всегда обновляются атомарно (вместе или не обновляются вовсе).
                    val (newDistanceM, avgSpeedMps, kilocalories) = withContext(Dispatchers.Default) {
                        // effectiveCount: при первом вызове после возобновления с паузы
                        // пропускаем точки от якоря до сейчас, чтобы не засчитать движение
                        // совершённое пока запись была выключена (пауза).
                        val effectiveCount = maxOf(processedCount, resumeAnchorPointCount)
                        val delta = calculateTrainingStatsUseCase.calculateDeltaDistance(
                            points, effectiveCount
                        )
                        accumulatedDistanceM += delta

                        // Калории уже инкрементальны на уровне точки, поэтому считаем
                        // только вклад новых точек, начиная с effectiveCount.
                        var deltaKcal = 0.0
                        for (index in effectiveCount until points.size) {
                            deltaKcal += points[index].calories ?: 0.0
                        }
                        val kcal = currentKilocalories + deltaKcal

                        processedCount = points.size
                        // Якорь одноразовый — сбрасываем после первого применения
                        if (resumeAnchorPointCount > 0) resumeAnchorPointCount = 0

                        // Длительность по монотонным часам (elapsedNanos) — не зависит от NTP/смены времени.
                        // Известное ограничение: elapsedNanos продолжает тикать во время паузы,
                        // поэтому пауза включается в итоговую длительность.
                        val durationSeconds = if (points.size >= 2)
                            (points.last().elapsedNanos - points.first().elapsedNanos) / 1_000_000_000L
                        else 0L

                        val speed = if (durationSeconds > 0) accumulatedDistanceM / durationSeconds else 0.0
                        Triple(accumulatedDistanceM, speed, kcal)
                    }

                    _state.update { it.copy(
                        distanceDisplay = "%.2f км".format(newDistanceM / 1000.0),
                        avgSpeedDisplay = formatPace(avgSpeedMps),
                        caloriesDisplay = "${kilocalories.toInt()} кКал",
                        distanceMeters  = newDistanceM,
                        kilocalories    = kilocalories,
                        trackPoints     = points,
                    ) }
                }
        }
    }

    /**
     * Запускает GPS в «discovery»-режиме: сервис пишет точки под временным UUID,
     * наблюдатель обновляет только gpsStatus (статистика не считается).
     * Вызывается при инициализации ViewModel и после завершения тренировки.
     */
    private fun startDiscoveryGps() {
        val id = UUID.randomUUID().toString()
        discoveryTrainingId = id
        startDiscoveryLocationService(id)
        startGpsStatusObserver(id)
    }

    /**
     * Лёгкий наблюдатель за GPS-точками для discovery-фазы.
     * Обновляет gpsStatus (SEARCHING → ACQUIRED → UNAVAILABLE), не трогает статистику.
     * При таймауте переводит в UNAVAILABLE — сервис продолжает работать и ждать фикс.
     */
    private fun startGpsStatusObserver(trainingId: String) {
        discoveryObserverJob?.cancel()
        discoveryObserverJob = viewModelScope.launch {
            var timeoutJob: Job? = null
            fun restartTimeout() {
                timeoutJob?.cancel()
                timeoutJob = launch {
                    delay(LocationConfig.GPS_FIX_TIMEOUT_MS)
                    _state.update { it.copy(gpsStatus = GpsStatus.UNAVAILABLE) }
                }
            }
            restartTimeout()

            locationRepository.observePointsForTraining(trainingId).collectLatest { points ->
                if (points.isNotEmpty()) {
                    if (_state.value.gpsStatus != GpsStatus.ACQUIRED) {
                        _state.update { it.copy(gpsStatus = GpsStatus.ACQUIRED) }
                    }
                    restartTimeout()
                }
            }
        }
    }

    /**
     * Ставит цепочку GPS-sync → save_training в WorkManager.
     * GPS-точки загружаются первыми, чтобы сервер принял их до закрытия тренировки.
     * Уникальное имя по trainingId: KEEP — не заменяем уже запущенную цепочку.
     */
    private fun enqueueOfflineFinishWork(trainingId: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val gpsWork = OneTimeWorkRequestBuilder<SyncGpsPointsWorker>()
            .setInputData(workDataOf(SyncGpsPointsWorker.KEY_TRAINING_ID to trainingId))
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        val saveWork = OneTimeWorkRequestBuilder<SaveTrainingWorker>()
            .setInputData(workDataOf(SaveTrainingWorker.KEY_TRAINING_ID to trainingId))
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        workManager
            .beginUniqueWork("offline_finish_$trainingId", ExistingWorkPolicy.KEEP, gpsWork)
            .then(saveWork)
            .enqueue()
    }

    override fun onCleared() {
        super.onCleared()
        // Останавливаем сервис при уничтожении ViewModel (навигация прочь с экрана).
        stopLocationService()
        // Если пользователь ушёл с экрана не начав тренировку — discovery UUID ещё жив.
        // Сервис успеет вызвать onDestroy и сам удалит точки (wasDiscovery = true),
        // но на случай Android service-reuse добавляем явное удаление здесь тоже.
        val discId = discoveryTrainingId
        if (discId != null) {
            viewModelScope.launch {
                locationRepository.deletePointsForTraining(discId)
            }
            discoveryTrainingId = null
        }
    }

    private fun loadFavoriteIds(): Set<String> {
        val raw = context.getSharedPreferences("workout_prefs", Context.MODE_PRIVATE)
            .getString("favorite_activity_ids", "") ?: ""
        return if (raw.isBlank()) emptySet() else raw.split(",").toSet()
    }

    /**
     * Запускает GPS-сервис для реальной тренировки с экстрами профиля пользователя.
     * Профиль используется в LocationTrackingService для расчёта калорий методом MET.
     *
     * Если profile null — калории будут null для всех точек (graceful degradation).
     */
    private fun startLocationService(trainingId: String) {
        val selectedType = _state.value.selectedType ?: return
        val intervalMs = when (selectedType.iconKey) {
            "3"  -> LocationConfig.INTERVAL_MS_CYCLING
            else -> LocationConfig.INTERVAL_MS_RUNNING
        }
        val accuracyThreshold = when (selectedType.iconKey) {
            "3"  -> LocationConfig.MAX_ACCURACY_CYCLING
            else -> LocationConfig.MAX_ACCURACY_RUNNING
        }

        val intent = Intent(context, LocationTrackingService::class.java).apply {
            putExtra(LocationTrackingService.EXTRA_TRAINING_ID, trainingId)
            putExtra(LocationTrackingService.EXTRA_INTERVAL_MS, intervalMs)
            putExtra(LocationTrackingService.EXTRA_ACCURACY_THRESHOLD, accuracyThreshold)

            // ── Экстры профиля для расчёта калорий ──────────────────────────────
            // Передаём type_activ_id для загрузки MET-данных из workoutRepository
            putExtra(LocationTrackingService.EXTRA_TYPE_ACTIV_ID, selectedType.id)

            // Передаём weight/height/age/gender если они доступны в профиле
            userProfile?.let { profile ->
                profile.weight?.let { putExtra(LocationTrackingService.EXTRA_WEIGHT_KG, it) }
                profile.height?.let { putExtra(LocationTrackingService.EXTRA_HEIGHT_CM, it) }
                // Рассчитываем возраст из birthDate
                val today = LocalDate.now()
                val rawAgeYears = Period.between(profile.birthDate, today).years
                if (profile.birthDate.isAfter(today)) {
                    Log.w(
                        "WorkoutStartViewModel",
                        "Ignoring future birthDate when calculating age: ${profile.birthDate}"
                    )
                }
                val ageYears = rawAgeYears.coerceAtLeast(0)
                putExtra(LocationTrackingService.EXTRA_AGE_YEARS, ageYears)
                putExtra(LocationTrackingService.EXTRA_IS_MALE, profile.gender == Gender.MALE)
            }
        }
        context.startForegroundService(intent)
    }

    /**
     * Запускает GPS-сервис для discovery-фазы (без профиля и MET-данных).
     * Discovery работает при открытии экрана, до нажатия «Начать тренировку».
     *
     * Намеренно не использует `selectedType ?: return`: сервис должен стартовать сразу
     * при открытии экрана, даже если типы ещё не загрузились (сетевой запрос).
     * В этом случае применяются дефолтные настройки (RUNNING), как было до добавления профиля.
     */
    private fun startDiscoveryLocationService(trainingId: String) {
        val iconKey = _state.value.selectedType?.iconKey
        val intervalMs = when (iconKey) {
            "3"  -> LocationConfig.INTERVAL_MS_CYCLING
            else -> LocationConfig.INTERVAL_MS_RUNNING
        }
        val accuracyThreshold = when (iconKey) {
            "3"  -> LocationConfig.MAX_ACCURACY_CYCLING
            else -> LocationConfig.MAX_ACCURACY_RUNNING
        }

        val intent = Intent(context, LocationTrackingService::class.java).apply {
            putExtra(LocationTrackingService.EXTRA_TRAINING_ID, trainingId)
            putExtra(LocationTrackingService.EXTRA_INTERVAL_MS, intervalMs)
            putExtra(LocationTrackingService.EXTRA_ACCURACY_THRESHOLD, accuracyThreshold)
            // Discovery: точки не пишутся в Room, не синхронизируются с сервером
            putExtra(LocationTrackingService.EXTRA_IS_DISCOVERY, true)
        }
        context.startForegroundService(intent)
    }

    private fun stopLocationService() {
        val intent = Intent(context, LocationTrackingService::class.java)
        context.stopService(intent)
    }

    companion object {
        private const val TAG = "WorkoutStartViewModel"

        /**
         * Форматирует миллисекунды в строку "HH:MM:SS" с ведущими нулями.
         * Используется для таймера тренировки.
         */
        fun formatDuration(elapsedMs: Long): String {
            val totalSec = elapsedMs / 1000L
            val hours   = totalSec / 3600L
            val minutes = (totalSec % 3600L) / 60L
            val seconds = totalSec % 60L
            return "%02d:%02d:%02d".format(hours, minutes, seconds)
        }

        /**
         * Форматирует скорость (м/с) в темп "M:SS мин/км".
         * При нулевой скорости возвращает "00:00 мин/км".
         *
         * Темп = 1000 / (avgSpeedMps * 60) мин/км:
         * - avgSpeedMps = 3.0 м/с → 5:33 мин/км
         * - avgSpeedMps = 4.0 м/с → 4:10 мин/км
         */
        fun formatPace(avgSpeedMps: Double): String {
            if (avgSpeedMps <= 0.0) return "00:00 мин/км"
            val paceSecPerKm = 1000.0 / avgSpeedMps
            val paceMin = (paceSecPerKm / 60).toInt()
            val paceSec = (paceSecPerKm % 60).toInt()
            return "$paceMin:${paceSec.toString().padStart(2, '0')} мин/км"
        }

        /**
         * Форматирует текущую дату в строку "25.02.2026 (Среда)".
         * java.time доступен нативно при minSdk=26 — desugaring не нужен.
         */
        fun formatCurrentDate(): String {
            val locale = Locale("ru")
            val date = LocalDate.now()
            val dateStr = date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy", locale))
            val dayStr = date.format(DateTimeFormatter.ofPattern("EEEE", locale))
                .replaceFirstChar { it.uppercase(locale) }
            return "$dateStr ($dayStr)"
        }
    }
}
