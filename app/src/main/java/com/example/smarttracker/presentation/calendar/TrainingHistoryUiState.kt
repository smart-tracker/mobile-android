package com.example.smarttracker.presentation.calendar

import com.example.smarttracker.domain.model.TrainingHistoryItem
import com.example.smarttracker.domain.model.WorkoutType
import java.time.LocalDate

/**
 * Режим просмотра истории тренировок.
 *
 * Переключение: пинч-зум (spread → zoomIn — детальнее, pinch → zoomOut — обзорнее).
 * Тап на день в Week view → DAY для той даты.
 * Тап на неделю в Month view → WEEK для той недели.
 * Кнопка «Назад» → предыдущий режим из бэкстека.
 */
enum class HistoryViewMode {
    DAY, WEEK, MONTH;

    fun zoomIn() = when (this) { MONTH -> WEEK; WEEK -> DAY; DAY -> DAY }
    fun zoomOut() = when (this) { DAY -> WEEK; WEEK -> MONTH; MONTH -> MONTH }
}

/**
 * UI-состояние экрана истории тренировок.
 *
 * [selectedDate] — опорная дата для вычисления периода:
 *  - DAY: тренировки за этот день
 *  - WEEK: неделя Пн–Вс, в которую попадает дата
 *  - MONTH: месяц, в который попадает дата
 *
 * [backStack] — стек пар (режим, дата) для кнопки «Назад».
 * При каждой навигации текущее состояние пушится в стек.
 */
data class TrainingHistoryUiState(
    val isLoading: Boolean = true,
    val items: List<TrainingHistoryItem> = emptyList(),
    val workoutTypes: List<WorkoutType> = emptyList(),
    val error: String? = null,
    val viewMode: HistoryViewMode = HistoryViewMode.DAY,
    val selectedDate: LocalDate = LocalDate.now(),
    val backStack: List<Pair<HistoryViewMode, LocalDate>> = emptyList(),
)
