package com.example.smarttracker.presentation.workout.start

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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

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
        val currentDate: String = formatCurrentDate(),
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
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    // UUID тренировки: генерируется при первом старте, сохраняется при паузе/возобновлении,
    // сбрасывается в null при завершении (onFinishClick).
    private var currentTrainingId: String? = null

    // Таймер — отдельный Job, считает wall-clock время с учётом паузы
    private var timerJob: Job? = null
    private var startTimeMs: Long = 0L
    private var pausedElapsedMs: Long = 0L

    // Наблюдатель за GPS-точками: обновляет GPS-статус и статистику
    private var observerJob: Job? = null

    init {
        loadWorkoutTypes()
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

    /** Нажатие «Начать тренировку» — запускает таймер и сервис трекинга */
    fun onStartWorkoutClick() {
        // trainingId генерируется один раз за сессию; при паузе/возобновлении ID не меняется
        val trainingId = currentTrainingId ?: UUID.randomUUID().toString()
            .also { currentTrainingId = it }

        // Таймер стартует от сохранённой паузы: при первом старте pausedElapsedMs = 0
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

        _state.update { it.copy(isTracking = true, gpsStatus = GpsStatus.SEARCHING) }

        startLocationService(trainingId)
        observeTrackingData(trainingId)
    }

    /** Нажатие «Пауза» — замораживает таймер, останавливает сервис */
    fun onPauseClick() {
        pausedElapsedMs = _state.value.elapsedMs
        timerJob?.cancel()
        _state.update { it.copy(isTracking = false) }
        stopLocationService()
        observerJob?.cancel()
    }

    /** Нажатие «Завершить» — останавливает всё и сбрасывает статистику в ноль */
    fun onFinishClick() {
        timerJob?.cancel()
        pausedElapsedMs = 0L
        _state.update { it.copy(
            isTracking      = false,
            gpsStatus       = GpsStatus.SEARCHING,
            elapsedMs       = 0L,
            timerDisplay    = "00:00:00",
            distanceDisplay = "0.00 км",
            avgSpeedDisplay = "00:00 мин/км",
            caloriesDisplay = "0 кКал",
            trackPoints     = emptyList(),
            mapTilesFailed  = false,
        ) }
        stopLocationService()
        observerJob?.cancel()
        currentTrainingId = null
        // Сбрасываем флаг офлайн-загрузки: следующая тренировка может быть в другом месте
        offlineMapManager.reset()
    }

    /** Карта сообщила, что тайлы недоступны (нет сети + нет кэша). Показываем fallback. */
    fun onMapTilesFailed() {
        _state.update { it.copy(mapTilesFailed = true) }
    }

    /** Клик по иконке в быстром ряду — только меняет selectedType */
    fun onQuickTypeSelected(type: WorkoutType) {
        _state.update { it.copy(selectedType = type) }
    }

    /** Выбор из шторки — тип встаёт на первое место */
    fun onSheetTypeSelected(type: WorkoutType) {
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
                        val delta = calculateTrainingStatsUseCase.calculateDeltaDistance(
                            points, processedCount
                        )
                        accumulatedDistanceM += delta
                        processedCount = points.size

                        // Длительность по монотонным часам (elapsedNanos) — не зависит от NTP/смены времени.
                        // Известное ограничение: elapsedNanos продолжает тикать во время паузы,
                        // поэтому пауза включается в итоговую длительность.
                        val durationSeconds = if (points.size >= 2)
                            (points.last().elapsedNanos - points.first().elapsedNanos) / 1_000_000_000L
                        else 0L

                        val speed = if (durationSeconds > 0) accumulatedDistanceM / durationSeconds else 0.0
                        val kcal = (accumulatedDistanceM / 1000.0 * 70.0).toFloat()
                        Triple(accumulatedDistanceM, speed, kcal)
                    }

                    _state.update { it.copy(
                        distanceDisplay = "%.2f км".format(newDistanceM / 1000.0),
                        avgSpeedDisplay = formatPace(avgSpeedMps),
                        caloriesDisplay = "${kilocalories.toInt()} кКал",
                        trackPoints     = points,
                    ) }
                }
        }
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
