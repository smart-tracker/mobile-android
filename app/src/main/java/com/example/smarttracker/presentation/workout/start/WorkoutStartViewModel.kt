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
import com.example.smarttracker.presentation.workout.summary.SummaryOrigin
import com.example.smarttracker.presentation.workout.summary.WorkoutSummaryFormatters
import com.example.smarttracker.presentation.workout.summary.WorkoutSummaryUiState
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
import com.example.smarttracker.domain.model.TrainingHistoryItem
import com.example.smarttracker.presentation.workout.summary.CumulativeTrackData
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
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
         * Индексы первой точки после каждого resume (0-based в [trackPoints]).
         * Пара (i-1, i) где i ∈ pauseGapIndices — gap-пара: точки разделены паузой,
         * haversine и разница timestampUtc не отражают реальное движение.
         * Используется [buildCumulativeData] для симметрии с live-расчётами:
         *   - дистанция: gap-пара пропускается (≡ resumeAnchorPointCount + 1)
         *   - elapsed: время паузы вычитается (≡ pausedElapsedMs в таймере)
         */
        val pauseGapIndices: List<Int> = emptyList(),
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
        /**
         * Снимок итогов завершённой тренировки. Не null после нажатия «Завершить» и до
         * закрытия оверлея. Используется UI-слоем для отрисовки слоя итогов поверх карты —
         * без навигации на отдельный экран. Карта (та же composable-инстанция) остаётся
         * жива, поэтому LocationComponent не разбирается → нет крашей анимаций MapLibre.
         */
        val summaryOverlay: WorkoutSummaryUiState? = null,
        /**
         * Полноэкранный режим карты внутри оверлея итогов (Figma 723:460):
         * скрывает шапку/активность/статистику и показывает только карту с overlay-картой
         * статистики и нижним слайдером.
         */
        val isMapFullscreen: Boolean = false,
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
     * unix-ms момента первого нажатия «Начать тренировку».
     * Фиксируется один раз при старте и используется в снимке итогов для отображения даты.
     * 0L означает, что тренировка ещё не начиналась.
     */
    private var trainingStartTimestamp: Long = 0L

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

    // true если сервер подтвердил регистрацию тренировки (startTraining вернул serverUUID).
    // false = тренировка начата офлайн с localUUID, при финише нужно сохранить typeActivId
    // в PendingFinish чтобы SyncGpsPointsWorker смог зарегистрировать её позже.
    private var isTrainingRegisteredOnServer: Boolean = false

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

    // Загрузка GPS-трека для превью истории. Отменяется в onCloseSummaryOverlay,
    // чтобы поздний ответ сети не возрождал уже закрытый оверлей.
    private var historyDetailJob: Job? = null

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
     * Нажатие «Начать тренировку» — немедленно стартует foreground-сервис (уведомление
     * появляется мгновенно), затем в фоне регистрирует тренировку на сервере.
     *
     * При возобновлении после паузы trainingId уже существует — сервер не вызывается повторно.
     * При ошибке сети — остаётся на localUUID; SyncGpsPointsWorker зарегистрирует тренировку
     * на сервере при появлении сети через [typeActivId] в PendingFinishEntity.
     */
    fun onStartWorkoutClick() {
        val selectedType = _state.value.selectedType ?: return

        // Защита от двойного тапа
        if (_state.value.isStarting) return

        // Возобновление после паузы — ID уже получен, сервер не вызываем
        if (currentTrainingId != null) {
            resumeTracking()
            return
        }

        // Генерируем localUUID и стартуем сервис немедленно — уведомление появляется сразу
        val localUUID = UUID.randomUUID().toString()
        currentTrainingId = localUUID
        isTrainingRegisteredOnServer = false
        _state.update { it.copy(isStarting = true) }
        resumeTracking()

        // Регистрируем тренировку на сервере в фоне параллельно с GPS-трекингом
        viewModelScope.launch {
            workoutRepository.startTraining(selectedType.id)
                .onSuccess { result ->
                    // Guard: onFinishClick мог сработать пока startTraining был in-flight.
                    // currentTrainingId == null означает, что тренировка уже завершена.
                    // Re-key нельзя делать: offline-цепочка для localUUID уже enqueued.
                    // Открытая serverUUID-тренировка закроется через finishOrphanedAndRetryStart
                    // при следующем старте.
                    if (currentTrainingId == null) {
                        Log.w(TAG, "startTraining returned after onFinishClick — discarding re-key to ${result.activeTrainingId}")
                        _state.update { it.copy(isStarting = false) }
                        return@onSuccess
                    }
                    val serverUUID = result.activeTrainingId
                    if (serverUUID != localUUID) {
                        // Intent сначала: сервис прекращает писать под localUUID
                        // до re-key в Room — уменьшает окно гонки до минимума
                        context.startService(
                            Intent(context, LocationTrackingService::class.java)
                                .putExtra(LocationTrackingService.EXTRA_TRAINING_ID_UPDATE, serverUUID)
                        )
                        observerJob?.cancel()
                        locationRepository.rekeyTrainingId(localUUID, serverUUID)
                        currentTrainingId = serverUUID
                        observeTrackingData(serverUUID)
                    }
                    isTrainingRegisteredOnServer = true
                    _state.update { it.copy(isStarting = false) }
                }
                .onFailure { error ->
                    when (error) {
                        is ActiveTrainingConflictException -> {
                            // На сервере незавершённая тренировка — завершаем её и повторяем старт.
                            // Сервис уже запущен с localUUID — пауза на время resolve.
                            finishOrphanedAndRetryStart(selectedType.id, localUUID)
                        }
                        else -> {
                            // Нет сети или другая ошибка — остаёмся на localUUID.
                            // SyncGpsPointsWorker зарегистрирует тренировку при появлении сети.
                            val message = if (error is NetworkUnavailableException) {
                                "Нет связи с сервером. Тренировка сохраняется локально."
                            } else {
                                "Не удалось зарегистрировать тренировку. Тренировка сохраняется локально."
                            }
                            _state.update { it.copy(
                                isStarting = false,
                                errorMessage = message,
                            ) }
                        }
                    }
                }
        }
    }

    /**
     * Автоматически завершает «осиротевшую» тренировку на сервере и повторяет старт.
     *
     * Вызывается когда сервер вернул 400 (ActiveTrainingConflictException). Сервис уже
     * запущен с [localUUID] — запись приостанавливается на время resolve, затем
     * возобновляется с правильным serverUUID (или остаётся на localUUID при ошибке сети).
     *
     * Флоу: пауза → GET /training/active → POST save_training → POST start → re-key → resume.
     */
    private suspend fun finishOrphanedAndRetryStart(typeActivId: Int, localUUID: String) {
        // Приостанавливаем запись GPS-точек на время resolve
        LocationTrackingService.setRecording(context, false)

        val activeId = workoutRepository.getActiveTraining().getOrNull()
        if (activeId == null) {
            Log.w(TAG, "finishOrphanedAndRetryStart: getActiveTraining() failed, keeping localUUID")
            isTrainingRegisteredOnServer = false
            _state.update { it.copy(
                isStarting = false,
                errorMessage = "Не удалось завершить предыдущую тренировку. Тренировка сохраняется локально.",
            ) }
            LocationTrackingService.setRecording(context, true)
            return
        }

        workoutRepository.saveTraining(
            trainingId          = activeId,
            timeEnd             = Instant.now()
                .atZone(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            totalDistanceMeters = null,
            totalKilocalories   = null,
        ).onFailure { e ->
            Log.w(TAG, "finishOrphanedAndRetryStart: saveTraining failed, keeping localUUID", e)
            isTrainingRegisteredOnServer = false
            _state.update { it.copy(
                isStarting = false,
                errorMessage = "Не удалось сохранить предыдущую тренировку на сервер. Новая тренировка сохраняется локально.",
            ) }
            LocationTrackingService.setRecording(context, true)
            return
        }

        // Передаём реальное время старта: между resumeTracking() и конфликтом (3 сетевых
        // вызова) прошло 1–5 сек — GPS-точки уже есть с timestampUtc < time_start сервера.
        // timeStart фиксирует момент нажатия «Начать», как и в offline-финише через WorkManager.
        workoutRepository.startTraining(
            typeActivId,
            Instant.ofEpochMilli(trainingStartTimestamp)
                .atZone(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        )
            .onSuccess { result ->
                val serverUUID = result.activeTrainingId
                // Intent сначала — сервис переключается на serverUUID до re-key в Room
                context.startService(
                    Intent(context, LocationTrackingService::class.java)
                        .putExtra(LocationTrackingService.EXTRA_TRAINING_ID_UPDATE, serverUUID)
                )
                observerJob?.cancel()
                locationRepository.rekeyTrainingId(localUUID, serverUUID)
                currentTrainingId = serverUUID
                isTrainingRegisteredOnServer = true
                observeTrackingData(serverUUID)
                _state.update { it.copy(isStarting = false) }
                LocationTrackingService.setRecording(context, true)
            }
            .onFailure {
                Log.e(TAG, "finishOrphanedAndRetryStart: retry startTraining failed", it)
                isTrainingRegisteredOnServer = false
                _state.update { it.copy(
                    isStarting = false,
                    errorMessage = "Нет связи с сервером. Тренировка сохраняется локально.",
                ) }
                LocationTrackingService.setRecording(context, true)
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
            // Фиксируем момент первого старта — используется на экране итогов для даты.
            // Пауза/возобновление не сбрасывают это значение.
            trainingStartTimestamp = System.currentTimeMillis()
            discoveryObserverJob?.cancel()
            discoveryObserverJob = null
            // Захватываем discId до обнуления — нужен для асинхронного удаления из Room.
            val discId = discoveryTrainingId
            discoveryTrainingId = null
            // Переводим сервис из discovery в тренировку без цикла stop/start:
            // EXTRA_TRANSITION_TO_WORKOUT вызывает onStartCommand() на живом экземпляре
            // → startForeground() срабатывает мгновенно без задержки destroy/create.
            transitionToWorkout(trainingId)
            // Discovery-точки удаляем асинхронно. Сервис уже переключил trainingId,
            // поэтому новые точки пишутся под workout-id, а старые (discId) ждут удаления.
            if (discId != null) {
                viewModelScope.launch {
                    locationRepository.deletePointsForTraining(discId)
                }
            }
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
        val gapIdx = _state.value.trackPoints.size  // первая точка после resume будет здесь
        resumeAnchorPointCount = gapIdx + 1
        _state.update { it.copy(pauseGapIndices = it.pauseGapIndices + gapIdx) }
        // GPS-трекер продолжает работать (сервис жив), но точки в Room не пишутся
        LocationTrackingService.setRecording(context, false)
    }

    /**
     * Нажатие «Завершить» — останавливает трекинг и показывает оверлей с итогами
     * поверх текущего экрана. Без навигации: тот же composable WorkoutStartScreen
     * рендерит оверлей через `state.summaryOverlay`. Это сохраняет ту же инстанцию
     * MapView, поэтому LocationComponent не разбирается → нет крашей анимаций MapLibre
     * (бывшая проблема с переходом на отдельный WorkoutSummaryScreen).
     *
     * saveTraining выполняется fire-and-forget: при ошибке данные остаются в Room,
     * пользователь не блокируется.
     *
     * Сброс live-полей (trackPoints, timer, distance) выполняется не здесь, а
     * в [onCloseSummaryOverlay] — это позволяет карте показывать тот же маршрут
     * во время оверлея и переключаться в полноэкранный режим без перерисовки.
     * Discovery-GPS тоже перезапускается при закрытии оверлея, не сейчас.
     */
    fun onFinishClick() {
        val trainingId = currentTrainingId
        val state = _state.value

        timerJob?.cancel()
        stopLocationService()
        observerJob?.cancel()
        discoveryObserverJob?.cancel()
        discoveryObserverJob = null

        // ── Снимок итогов для оверлея (Figma 688:532) ───────────────────────
        // Все поля форматируются единообразно с экраном WorkoutSummaryScreen,
        // потому что переиспользуем тип WorkoutSummaryUiState — позволит в будущем
        // вынести общие composable-ы оверлея и истории тренировок.
        val summaryDistanceKm = (state.distanceMeters / 1000.0).toFloat()
        val summaryDurationMs = state.elapsedMs
        // Набор высоты считается полным проходом по trackPoints здесь, в момент
        // завершения тренировки — отображается только на экране итогов.
        val summaryElevationM = calculateElevationGain(state.trackPoints).toFloat()
        val summaryStartTs    = if (trainingStartTimestamp > 0L)
                                    trainingStartTimestamp
                                else
                                    System.currentTimeMillis()
        val type = state.selectedType
        val snapshot = WorkoutSummaryUiState(
            origin           = SummaryOrigin.FINISH,
            dateDisplay      = WorkoutSummaryFormatters.formatDate(summaryStartTs),
            activityName     = type?.name.orEmpty(),
            activityIconFile = type?.iconFile,
            activityIconUrl  = type?.imageUrl,
            activityIconKey  = type?.iconKey.orEmpty(),
            paceDisplay      = WorkoutSummaryFormatters.formatPace(summaryDistanceKm, summaryDurationMs),
            distanceDisplay  = WorkoutSummaryFormatters.formatDistance(summaryDistanceKm),
            durationDisplay  = WorkoutSummaryFormatters.formatDuration(summaryDurationMs),
            elevationDisplay = WorkoutSummaryFormatters.formatElevation(summaryElevationM),
            trackPoints      = state.trackPoints,
            cumulativeData   = buildCumulativeData(state.trackPoints, state.pauseGapIndices),
            isLoading        = false,
        )

        // Отправить итоги на сервер (fire-and-forget с офлайн-fallback)
        if (trainingId != null) {
            // Фиксируем timeEnd один раз — используется и в сетевом запросе,
            // и в очереди, чтобы оба варианта содержали одинаковое время завершения.
            val timeEnd = Instant.now()
                .atZone(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            // Шлём фактические значения даже при 0.0 — иначе Gson дропает null-поля,
            // и в тело /save_training уходит только {"time_end":"..."} без
            // total_distance_meters / total_kilocalories. Бэк на таком пустом
            // теле падает с 500 (баг сервера, не обрабатывает Optional как
            // отсутствующее поле). 0.0 валиден семантически: тренировка без
            // движения → 0 м, 0 ккал. Подтверждено в логе 2026-05-18 19:41
            // (training_id 7aa98edb-..., 1 GPS-точка, 17 сек → 500).
            val distanceMeters = state.distanceMeters
            val kilocalories   = state.kilocalories

            if (!isTrainingRegisteredOnServer) {
                // Тренировка не зарегистрирована на сервере (startTraining не успел или нет сети).
                // Прямой вызов saveTraining(localUUID) вернёт 404 — сервер не знает этот ID.
                // Сразу идём в WorkManager: сначала зарегистрирует тренировку, потом загрузит
                // GPS-точки и закроет. Не ждём finishSyncFlow — сервис всё равно не смог
                // синхронизировать точки в реальном времени (тренировки нет на сервере).
                Log.w(TAG, "Training not registered on server, queuing full offline sync for $trainingId")
                viewModelScope.launch {
                    // NonCancellable: оба вызова не прерываются при уничтожении ViewModel
                    // (поворот экрана, смахивание приложения). Без этого enqueueOfflineFinishWork
                    // могла быть скипнута если ViewModel умерла между savePendingFinish и enqueue:
                    // pending-запись в Room без WorkManager-цепочки → тренировка никогда не
                    // синхронизируется пока пользователь не откроет приложение заново.
                    withContext(NonCancellable) {
                        workoutRepository.savePendingFinish(
                            trainingId          = trainingId,
                            timeEnd             = timeEnd,
                            totalDistanceMeters = distanceMeters,
                            totalKilocalories   = kilocalories,
                            typeActivId         = _state.value.selectedType?.id,
                            // Реальное время старта тренировки: фиксируется при нажатии «Начать».
                            // Передаётся в POST /training/start чтобы бэкенд записал правильный
                            // time_start — иначе сервер ставит время синхронизации (после сети),
                            // которое всегда позже time_end → невалидные данные в истории.
                            // Формат ISO_OFFSET_DATE_TIME (+00:00) совпадает с timeEnd —
                            // Instant.toString() даёт суффикс Z, что вызывает ошибки парсинга
                            // на FastAPI если сервер ожидает единообразный формат со смещением.
                            timeStart           = Instant.ofEpochMilli(trainingStartTimestamp)
                                .atZone(ZoneOffset.UTC)
                                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        )
                        enqueueOfflineFinishWork(trainingId)
                    }
                }
            } else {
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
                                // Сеть пропала после регистрации — ставим в очередь без typeActivId:
                                // тренировка уже есть на сервере, WorkManager только закроет её.
                                // NonCancellable: запись в Room не прерывается если ViewModel
                                // уничтожается прямо в этот момент (пользователь закрыл приложение).
                                Log.w(TAG, "saveTraining failed (no network), queuing for later delivery")
                                withContext(NonCancellable) {
                                    workoutRepository.savePendingFinish(
                                        trainingId          = trainingId,
                                        timeEnd             = timeEnd,
                                        totalDistanceMeters = distanceMeters,
                                        totalKilocalories   = kilocalories,
                                        typeActivId         = null,
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
        }

        // Live-поля (timer/distance/trackPoints) НЕ сбрасываем здесь — карта продолжает
        // показывать пройденный маршрут под оверлеем, а сам оверлей тянет данные из
        // [snapshot]. Полный сброс выполняется в [onCloseSummaryOverlay].
        _state.update { it.copy(
            isTracking       = false,
            isWorkoutStarted = false,
            summaryOverlay   = snapshot,
            isMapFullscreen  = false,
            mapTilesFailed   = false,
            // Точки перенесены в snapshot.trackPoints — освобождаем live-список.
            // Карта читает трек из summary?.trackPoints в WorkoutStartScreen.
            trackPoints      = emptyList(),
        ) }
        currentTrainingId = null
        isTrainingRegisteredOnServer = false
    }

    /**
     * Закрытие оверлея итогов.
     *
     * Поведение зависит от [WorkoutSummaryUiState.origin]:
     *  - [SummaryOrigin.FINISH]  — оверлей завершённой тренировки → возвращаемся в состояние
     *    «можно начать новую тренировку»: сбрасываем live-поля и рестартуем discovery-GPS.
     *  - [SummaryOrigin.HISTORY] — превью из истории → закрываем только оверлей, не трогая
     *    live-состояние. Если параллельно идёт активная тренировка, её данные сохраняются.
     *    Дополнительно отменяем [historyDetailJob], чтобы поздний ответ сети не возрождал
     *    уже закрытый оверлей.
     *
     * Вызывается:
     *  - тапом стрелки «назад» в оверлее
     *  - системной кнопкой Back (через BackHandler в UI)
     *  - переключением вкладки в нижнем баре (см. WorkoutHomeScreen)
     */
    fun onCloseSummaryOverlay() {
        val overlay = _state.value.summaryOverlay ?: return

        if (overlay.origin == SummaryOrigin.HISTORY) {
            // История: только закрываем оверлей, отменяем pending-загрузку.
            // Live-поля (trackPoints/elapsedMs/distance/...) НЕ трогаем —
            // если идёт активная тренировка, её состояние должно остаться.
            historyDetailJob?.cancel()
            historyDetailJob = null
            _state.update { it.copy(summaryOverlay = null, isMapFullscreen = false) }
            return
        }

        // FINISH: полный сброс live-полей и рестарт discovery-GPS
        pausedElapsedMs = 0L
        trainingStartTimestamp = 0L
        _state.update { it.copy(
            summaryOverlay  = null,
            isMapFullscreen = false,
            // Сохраняем ACQUIRED если GPS был активен — физически сигнал не пропал,
            // только завершилась тренировка. SEARCHING ставим если фикса не было.
            gpsStatus       = if (it.gpsStatus == GpsStatus.ACQUIRED)
                                  GpsStatus.ACQUIRED
                              else
                                  GpsStatus.SEARCHING,
            elapsedMs         = 0L,
            timerDisplay      = "00:00:00",
            distanceDisplay   = "0.00 км",
            avgSpeedDisplay   = "00:00 мин/км",
            caloriesDisplay   = "0 кКал",
            distanceMeters    = 0.0,
            kilocalories      = 0.0,
            trackPoints       = emptyList(),
            pauseGapIndices   = emptyList(),
            mapTilesFailed    = false,
        ) }
        offlineMapManager.reset()
        // Перезапускаем discovery-GPS: иконка остаётся живой между тренировками
        startDiscoveryGps()
    }

    /**
     * Переключение полноэкранного режима карты внутри оверлея итогов (Figma 723:460).
     * Тап по карте раскрывает её на весь экран; стрелка «назад» сворачивает обратно.
     */
    fun onToggleFullscreenMap() {
        _state.update { it.copy(isMapFullscreen = !it.isMapFullscreen) }
    }

    /**
     * Удаление тренировки из истории (вызов из SummaryOverlay по тапу на иконку корзины).
     *
     * Безопасный no-op если:
     *  - оверлей закрыт (overlay == null);
     *  - оверлей не HISTORY (FINISH-оверлей не должен предлагать удаление);
     *  - trainingId не известен (странный кейс, но fallback).
     *
     * После успеха закрываем оверлей через [onCloseSummaryOverlay] (не трогаем live-поля,
     * это HISTORY-ветка). TrainingHistoryViewModel сам перезагрузит список через
     * historyChangedFlow → loadHistory.
     */
    fun onDeleteHistoryTraining() {
        val overlay = _state.value.summaryOverlay ?: return
        if (overlay.origin != SummaryOrigin.HISTORY) return
        val trainingId = overlay.trainingId ?: return
        viewModelScope.launch {
            workoutRepository.deleteCompletedTraining(trainingId)
                .onSuccess { onCloseSummaryOverlay() }
                .onFailure { e -> Log.e(TAG, "deleteCompletedTraining failed for $trainingId", e) }
        }
    }

    /**
     * Показать SummaryOverlay для тренировки из истории.
     *
     * Сначала отображает overlay в состоянии загрузки ([WorkoutSummaryUiState.isLoading] = true),
     * затем асинхронно загружает GPS-трек через GET /training/{id}/get_training.
     * Если трек недоступен — overlay показывается со статистикой, но без карты.
     *
     * Используется из [com.example.smarttracker.presentation.workout.WorkoutHomeScreen]
     * при клике на карточку тренировки в Day view истории.
     */
    fun showHistorySummary(item: TrainingHistoryItem, activityName: String) {
        // Если уже идёт загрузка предыдущего превью — отменяем, чтобы её поздний
        // ответ не перетёр новый снимок.
        historyDetailJob?.cancel()
        _state.update { it.copy(summaryOverlay = WorkoutSummaryUiState(
            origin     = SummaryOrigin.HISTORY,
            trainingId = item.trainingId,
            isLoading  = true,
        )) }
        historyDetailJob = viewModelScope.launch {
            val points    = workoutRepository.getTrainingDetail(item.trainingId).getOrDefault(emptyList())
            val distanceKm = ((item.distanceM ?: 0.0) / 1000.0).toFloat()
            val durationMs = computeHistoryDurationMs(item.timeStart, item.timeEnd)
            val workoutType = _state.value.workoutTypes.find { it.id == item.typeActivId }
            val dateMs     = item.date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            // Приоритет: серверное elevation_gain из списка истории (item.elevationGain).
            // Fallback на клиентский расчёт по точкам — на случай старого ответа без поля
            // или null от сервера.
            val elevationM = (item.elevationGain ?: calculateElevationGain(points)).toFloat()
            val cumData    = buildCumulativeData(points, emptyList())
            val snapshot = WorkoutSummaryUiState(
                origin           = SummaryOrigin.HISTORY,
                trainingId       = item.trainingId,
                dateDisplay      = WorkoutSummaryFormatters.formatDate(dateMs),
                activityName     = activityName,
                activityIconFile = workoutType?.iconFile,
                activityIconUrl  = workoutType?.imageUrl,
                activityIconKey  = item.typeActivId.toString(),
                paceDisplay      = WorkoutSummaryFormatters.formatPace(distanceKm, durationMs),
                distanceDisplay  = WorkoutSummaryFormatters.formatDistance(distanceKm),
                durationDisplay  = WorkoutSummaryFormatters.formatDuration(durationMs),
                elevationDisplay = WorkoutSummaryFormatters.formatElevation(elevationM),
                trackPoints      = points,
                cumulativeData   = cumData,
            )
            // Защита от гонки: если оверлей закрыли пока шла загрузка, не возрождаем его.
            _state.update {
                if (it.summaryOverlay == null) it
                else it.copy(summaryOverlay = snapshot)
            }
        }
    }

    /** Парсит ISO-строки времени из истории API (формат "2026-05-16T08:44:00.613000Z"). */
    private fun computeHistoryDurationMs(start: String?, end: String?): Long {
        if (start == null || end == null) return 0L
        return try {
            val s = java.time.OffsetDateTime.parse(start.trim()).toLocalDateTime()
            val e = java.time.OffsetDateTime.parse(end.trim()).toLocalDateTime()
            java.time.Duration.between(s, e).toMillis()
        } catch (_: Exception) { 0L }
    }

    /**
     * Считает суммарный положительный набор высоты по GPS-точкам.
     *
     * Алгоритм прост: суммируем только положительные дельты altitude между соседними
     * точками. Точки с null-altitude пропускаем (не ломают цепочку, но и не дают
     * прироста). Вызывается один раз в [onFinishClick] — отображается только на
     * экране итогов, во время тренировки набор высоты не обновляется.
     *
     * TODO: фильтр шума GPS-альтиметра (±5–10 м) — см. CLAUDE.md пункт 19.
     */
    private fun calculateElevationGain(points: List<LocationPoint>): Double {
        if (points.size < 2) return 0.0
        var gain = 0.0
        var prevAlt: Double? = null
        for (p in points) {
            val alt = p.altitude ?: continue
            prevAlt?.let { prev -> if (alt - prev > 0) gain += alt - prev }
            prevAlt = alt
        }
        return gain
    }

    /**
     * Предвычисляет накопленные значения трека (дистанция, набор высоты, время) для
     * каждой GPS-точки. Используется для O(1) scrub-lookups при перемотке трека.
     * Вычисляется один раз в [onFinishClick] и хранится в снимке [WorkoutSummaryUiState].
     *
     * Симметрия с live-расчётами (критично для совпадения итогов в конце scrubbing):
     * - **Дистанция:** gap-пары (индексы из [pauseGapIndices]) пропускаются — аналогично
     *   `resumeAnchorPointCount + 1` в `observeTrackingData`.
     * - **Elapsed:** время каждой паузы (timestampUtc[gap] − timestampUtc[gap−1]) вычитается
     *   нарастающим итогом — аналогично `startTimeMs = now − pausedElapsedMs` в таймере.
     * - **Высота:** null-точки пропускаются через `prevAlt: Double?` — идентично
     *   `calculateElevationGain` (избегает ложных скачков +300 м при null→0.0).
     *
     * @param pauseGapIndices индексы первых точек после каждого resume (из [UiState.pauseGapIndices])
     */
    private fun buildCumulativeData(
        points: List<LocationPoint>,
        pauseGapIndices: List<Int>,
    ): com.example.smarttracker.presentation.workout.summary.CumulativeTrackData {
        if (points.size < 2) return com.example.smarttracker.presentation.workout.summary.CumulativeTrackData()
        val gapSet = pauseGapIndices.toHashSet()
        val n = points.size
        val distances = ArrayList<Float>(n)
        val elevations = ArrayList<Float>(n)
        val elapsed    = ArrayList<Long>(n)
        val speeds     = ArrayList<Float>(n)
        var cumDistM     = 0.0
        var cumElevM     = 0f
        var totalPausedMs = 0L
        val t0 = points.first().timestampUtc
        // prevAlt: последняя известная высота — зеркалит логику calculateElevationGain.
        // Если передавать (altitude ?: 0.0), null-точка трактуется как высота 0 → ложный
        // скачок +300 м при переходе от 300 м к null и затем снова к 305 м (60× расхождение).
        var prevAlt: Double? = points.first().altitude
        distances.add(0f); elevations.add(0f); elapsed.add(0L); speeds.add(0f)
        for (i in 1 until n) {
            val isGap = i in gapSet
            // Δdistance этого шага — нужен и для накопленной дистанции, и для скорости.
            var stepDistM = 0.0
            if (isGap) {
                // Gap-пара: пользователь стоял на паузе. Haversine не считаем — телепорт
                // не отражает реального движения. Время паузы вычитаем из elapsed.
                totalPausedMs += points[i].timestampUtc - points[i - 1].timestampUtc
            } else {
                // calculateDeltaDistance(points, i - 1) возвращает расстояние от точки (i-2)
                // до КОНЦА списка — не шаг, а хвост. Исправляем: передаём пару из двух
                // соседних точек, fromIndex=0 → startIdx=max(0,-1)=0 → haversine одного шага.
                stepDistM = calculateTrainingStatsUseCase.calculateDeltaDistance(
                    listOf(points[i - 1], points[i]), 0
                )
                cumDistM += stepDistM
                val altCur = points[i].altitude
                if (altCur != null && prevAlt != null) {
                    val dAlt = (altCur - prevAlt).toFloat()
                    if (dAlt > 0f) cumElevM += dAlt
                }
            }
            val altCur = points[i].altitude
            if (altCur != null) prevAlt = altCur  // обновляем prevAlt даже на gap-точках
            distances.add((cumDistM / 1000.0).toFloat())
            elevations.add(cumElevM)
            elapsed.add(points[i].timestampUtc - t0 - totalPausedMs)
            // Мгновенная скорость м/с. Для FINISH дублирует sensor-speed, но даёт single
            // source of truth (UI читает speeds[i] всегда из cumulativeData). Для HISTORY
            // sensor-speed = null, расчёт = только этот способ.
            // На gap-парах stepDistM=0 → speed=0, что корректно (пользователь стоял).
            // Защита от деления на ноль: dtMs <= 0 → 0 (для дубль-точек с одинаковым timestamp).
            val dtMs = points[i].timestampUtc - points[i - 1].timestampUtc
            val speedMs = if (dtMs > 0L) (stepDistM / (dtMs / 1000.0)).toFloat() else 0f
            speeds.add(speedMs)
        }
        return com.example.smarttracker.presentation.workout.summary.CumulativeTrackData(
            distancesKm = distances,
            elevationsM = elevations,
            elapsedMs   = elapsed,
            speedsMs    = speeds,
        )
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
            // Локальный аккумулятор калорий — симметричен accumulatedDistanceM.
            // НЕ читаем _state.value.kilocalories: при перезапуске observer'а после re-key
            // (localUUID → serverUUID) state уже содержит накопленное значение, и
            // currentKilocalories + deltaKcal дало бы K + K = 2K.
            var accumulatedKilocalories = 0.0
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

                    // Инкрементальный расчёт на фоновом потоке, чтобы не блокировать UI.
                    // Внутри withContext нет точек приостановки, поэтому collectLatest
                    // не может прервать блок посередине — все аккумуляторы обновляются атомарно.
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
                        for (index in effectiveCount until points.size) {
                            accumulatedKilocalories += points[index].calories ?: 0.0
                        }
                        val kcal = accumulatedKilocalories

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
     * Переводит работающий discovery-сервис в режим тренировки без остановки.
     * Отправляет [LocationTrackingService.EXTRA_TRANSITION_TO_WORKOUT] Intent через
     * startForegroundService() — onStartCommand() вызывается на живом экземпляре,
     * startForeground() срабатывает мгновенно без задержки destroy/create.
     *
     * Если сервис по каким-то причинам не запущен — startForegroundService() запустит
     * новый экземпляр, который корректно обработает EXTRA_TRANSITION_TO_WORKOUT.
     */
    private fun transitionToWorkout(trainingId: String) {
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
            putExtra(LocationTrackingService.EXTRA_TRANSITION_TO_WORKOUT, true)
            putExtra(LocationTrackingService.EXTRA_TRAINING_ID, trainingId)
            putExtra(LocationTrackingService.EXTRA_INTERVAL_MS, intervalMs)
            putExtra(LocationTrackingService.EXTRA_ACCURACY_THRESHOLD, accuracyThreshold)
            putExtra(LocationTrackingService.EXTRA_TYPE_ACTIV_ID, selectedType.id)
            userProfile?.let { profile ->
                profile.weight?.let { putExtra(LocationTrackingService.EXTRA_WEIGHT_KG, it) }
                profile.height?.let { putExtra(LocationTrackingService.EXTRA_HEIGHT_CM, it) }
                val today = LocalDate.now()
                val rawAgeYears = Period.between(profile.birthDate, today).years
                if (profile.birthDate.isAfter(today)) {
                    Log.w(TAG, "Ignoring future birthDate when calculating age: ${profile.birthDate}")
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
        context.startService(intent)
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
