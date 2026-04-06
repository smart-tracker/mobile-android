package com.example.smarttracker.presentation.workout.start

import android.util.Log
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarttracker.data.location.LocationConfig
import com.example.smarttracker.data.location.LocationTrackingService
import com.example.smarttracker.data.location.OfflineMapManager
import com.example.smarttracker.domain.model.LocationPoint
import com.example.smarttracker.domain.model.WorkoutType
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
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

        loadWorkoutTypes()
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

    private fun loadWorkoutTypes() {
        viewModelScope.launch {
            _state.update { it.copy(isTypesLoading = true) }
            workoutRepository.getWorkoutTypes()
                .onSuccess { types ->
                    _state.update { it.copy(
                        workoutTypes  = types,
                        pinnedTypes   = types.take(3),
                        selectedType  = types.firstOrNull(),
                        isTypesLoading = false,
                    ) }
                }
                .onFailure { error ->
                    _state.update { it.copy(
                        isTypesLoading = false,
                        errorMessage = error.localizedMessage ?: "Ошибка загрузки типов тренировок",
                    ) }
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
                .onFailure {
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
            isTracking      = true,
            isWorkoutStarted = true,
            // При первом старте переходим в SEARCHING, при возобновлении GPS уже работает
            gpsStatus       = if (isFirstStart) GpsStatus.SEARCHING else _state.value.gpsStatus,
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
        // Запоминаем сколько точек было до паузы — observeTrackingData пропустит
        // расстояние от этих точек до первой точки после возобновления
        resumeAnchorPointCount = _state.value.trackPoints.size
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

        // Отправить итоги на сервер (fire-and-forget)
        if (trainingId != null) {
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
                    trainingId = trainingId,
                    timeEnd = Instant.now()
                        .atZone(ZoneOffset.UTC)
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    totalDistanceMeters = state.distanceMeters.takeIf { it > 0 },
                    totalKilocalories   = state.kilocalories.takeIf { it > 0 },
                )
            }
        }

        pausedElapsedMs = 0L
        _state.update { it.copy(
            isTracking       = false,
            isWorkoutStarted = false,
            gpsStatus        = GpsStatus.SEARCHING,
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
     * GPS-таймаут: 30 сек без новых точек → UNAVAILABLE; перезапускается после каждой точки,
     * поэтому потеря сигнала после ACQUIRED тоже корректно обнаруживается.
     */
    private fun observeTrackingData(trainingId: String) {
        observerJob?.cancel()
        observerJob = viewModelScope.launch {
            // Инкрементальный счётчик живёт в скоупе coroutine — синхронизация не нужна
            var accumulatedDistanceM = 0.0
            var processedCount = 0

            // Дочерний Job таймаута: перезапускается после каждой новой точки,
            // чтобы корректно обнаруживать потерю сигнала и после ACQUIRED.
            var timeoutJob: Job? = null
            fun restartTimeout() {
                timeoutJob?.cancel()
                timeoutJob = launch {
                    delay(LocationConfig.GPS_FIX_TIMEOUT_MS)
                    // Сигнал недоступности GPS — переводим трекинг в тот же режим,
                    // что и ручная пауза, чтобы корректно остановить таймер,
                    // сохранить elapsed-время паузы и синхронно обновить UI.
                    onPauseClick()
                    _state.update { it.copy(
                        gpsStatus = GpsStatus.UNAVAILABLE,
                    ) }
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
                        val kcal = accumulatedDistanceM / 1000.0 * 70.0
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
        startLocationService(id)
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

    override fun onCleared() {
        super.onCleared()
        // Останавливаем сервис при уничтожении ViewModel (навигация прочь с экрана)
        stopLocationService()
    }

    private fun loadFavoriteIds(): Set<String> {
        val raw = context.getSharedPreferences("workout_prefs", Context.MODE_PRIVATE)
            .getString("favorite_activity_ids", "") ?: ""
        return if (raw.isBlank()) emptySet() else raw.split(",").toSet()
    }

    private fun startLocationService(trainingId: String) {
        val intervalMs = when (_state.value.selectedType?.iconKey) {
            "3"  -> LocationConfig.INTERVAL_MS_CYCLING
            else -> LocationConfig.INTERVAL_MS_RUNNING
        }
        val accuracyThreshold = when (_state.value.selectedType?.iconKey) {
            "3"  -> LocationConfig.MAX_ACCURACY_CYCLING
            else -> LocationConfig.MAX_ACCURACY_RUNNING
        }
        val intent = Intent(context, LocationTrackingService::class.java).apply {
            putExtra(LocationTrackingService.EXTRA_TRAINING_ID, trainingId)
            putExtra(LocationTrackingService.EXTRA_INTERVAL_MS, intervalMs)
            putExtra(LocationTrackingService.EXTRA_ACCURACY_THRESHOLD, accuracyThreshold)
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
