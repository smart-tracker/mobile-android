package com.example.smarttracker.presentation.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarttracker.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * ViewModel экрана истории тренировок.
 *
 * Навигация между периодами через пинч и тап:
 *  - [onZoomIn] / [onZoomOut] — смена режима DAY/WEEK/MONTH
 *  - [onDaySelected] — переход в Day view для конкретной даты (из Week view)
 *  - [onWeekSelected] — переход в Week view для конкретной недели (из Month view)
 *  - [onBack] — возврат к предыдущему режиму из бэкстека; возвращает false если стек пуст
 *
 * Каждое навигационное действие пушит текущий (режим, дата) в [TrainingHistoryUiState.backStack].
 * [onBack] делает pop и восстанавливает предыдущее состояние.
 */
@HiltViewModel
class TrainingHistoryViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TrainingHistoryUiState())
    val state: StateFlow<TrainingHistoryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            workoutRepository.workoutTypesFlow().collect { types ->
                _state.update { it.copy(workoutTypes = types) }
            }
        }
        // Автообновление истории при любом изменении: сохранение тренировки
        // (saveTraining, в т.ч. SaveTrainingWorker для офлайна) или удаление
        // (deleteCompletedTraining). historyChangedFlow эмитит единый триггер.
        viewModelScope.launch {
            workoutRepository.historyChangedFlow.collect {
                loadHistory()
            }
        }
        loadHistory()
    }

    fun loadHistory() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            workoutRepository.getTrainingHistory()
                .onSuccess { items -> _state.update { it.copy(isLoading = false, items = items) } }
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun onZoomIn() {
        val current = _state.value
        val newMode = current.viewMode.zoomIn()
        if (newMode == current.viewMode) return
        _state.update { it.copy(
            viewMode = newMode,
            backStack = it.backStack + (it.viewMode to it.selectedDate),
        ) }
    }

    fun onZoomOut() {
        val current = _state.value
        val newMode = current.viewMode.zoomOut()
        if (newMode == current.viewMode) return
        _state.update { it.copy(
            viewMode = newMode,
            backStack = it.backStack + (it.viewMode to it.selectedDate),
        ) }
    }

    fun onDaySelected(date: LocalDate) {
        _state.update { it.copy(
            viewMode = HistoryViewMode.DAY,
            selectedDate = date,
            backStack = it.backStack + (it.viewMode to it.selectedDate),
        ) }
    }

    fun onWeekSelected(weekStart: LocalDate) {
        _state.update { it.copy(
            viewMode = HistoryViewMode.WEEK,
            selectedDate = weekStart,
            backStack = it.backStack + (it.viewMode to it.selectedDate),
        ) }
    }

    /**
     * Сбрасывает просмотр на День / сегодня с очисткой бэкстека.
     * Вызывается при каждом входе на экран, чтобы всегда открывался текущий день.
     */
    fun resetToToday() {
        _state.update { it.copy(
            viewMode = HistoryViewMode.DAY,
            selectedDate = LocalDate.now(),
            backStack = emptyList(),
        ) }
    }

    /**
     * Возвращает предыдущий режим из бэкстека.
     * @return true если переход выполнен, false если стек пуст (система обработает Back).
     */
    fun onBack(): Boolean {
        val stack = _state.value.backStack
        if (stack.isEmpty()) return false
        val (prevMode, prevDate) = stack.last()
        _state.update { it.copy(
            viewMode = prevMode,
            selectedDate = prevDate,
            backStack = it.backStack.dropLast(1),
        ) }
        return true
    }
}
