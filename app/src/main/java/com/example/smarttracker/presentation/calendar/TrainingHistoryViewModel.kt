package com.example.smarttracker.presentation.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarttracker.data.local.SettingsStorage
import com.example.smarttracker.domain.repository.WorkoutRepository
import com.example.smarttracker.utils.ApiErrorHandler
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
    private val settingsStorage: SettingsStorage,
) : ViewModel() {

    private val _state = MutableStateFlow(TrainingHistoryUiState())
    val state: StateFlow<TrainingHistoryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            workoutRepository.workoutTypesFlow().collect { types ->
                _state.update { it.copy(workoutTypes = types) }
            }
        }
        // Флаг показанного онбординга: false → coachmark покажется автоматически
        // при первом заходе на экран (гейт в TrainingHistoryScreen).
        viewModelScope.launch {
            settingsStorage.settings.collect { s ->
                _state.update { it.copy(coachmarkShown = s.calendarCoachmarkShown) }
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
                .onFailure { e ->
                    // Понятное сообщение на русском (сеть/HTTP/прочее) вместо сырого e.message.
                    _state.update { it.copy(isLoading = false, error = ApiErrorHandler.getErrorMessage(e)) }
                }
        }
    }

    /**
     * Прямое переключение уровня табами (День/Неделя/Месяц). В отличие от пинча —
     * любой уровень напрямую. [anchorDate] — текущий верхний видимый период, чтобы
     * при смене уровня остаться на том же времени (не прыгать на старый selectedDate).
     */
    fun setViewMode(mode: HistoryViewMode, anchorDate: LocalDate) {
        val current = _state.value
        if (mode == current.viewMode) return
        _state.update { it.copy(
            viewMode = mode,
            selectedDate = anchorDate,
            backStack = it.backStack + (it.viewMode to it.selectedDate),
        ) }
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

    /**
     * Прыжок ленты к выбранной дате (из пикера в шапке). Меняет только якорь
     * прокрутки — режим и агрегация не трогаются; view нормализует дату к своему
     * периоду (день/неделя/месяц) через `periodIndexOf` и прокручивается туда.
     */
    fun jumpToDate(date: LocalDate) {
        _state.update { it.copy(selectedDate = date) }
    }

    /** «Понятно» в onboarding-coachmark — больше не показывать автоматически (персист). */
    fun onCoachmarkDismissed() {
        viewModelScope.launch { settingsStorage.setCalendarCoachmarkShown(true) }
    }
}
