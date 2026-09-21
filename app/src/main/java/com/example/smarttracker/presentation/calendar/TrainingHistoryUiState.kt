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
 * Раскладка экрана истории — независимая ось от [HistoryViewMode].
 * Обе раскладки работают во всех трёх режимах (День/Неделя/Месяц).
 *
 * [TREE] — «дерево»: ствол + ноды + карточки-листья слева/справа (основной вид).
 * [LIST] — «строчный»: тренировки рядами, фильтр по видам, сортировка,
 *          сворачиваемые блоки детальной статистики периодов.
 */
enum class HistoryLayout { TREE, LIST }

/** Поле сортировки строчной раскладки (направление — отдельным флагом `sortAsc`). */
enum class HistorySort { DATE, DURATION, DISTANCE, CALORIES }

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
 *
 * [coachmarkShown] — показан ли уже одноразовый onboarding-coachmark экрана
 * (персист в SettingsStorage). false → показать автоматически при первом заходе.
 */
data class TrainingHistoryUiState(
    val isLoading: Boolean = true,
    val items: List<TrainingHistoryItem> = emptyList(),
    val workoutTypes: List<WorkoutType> = emptyList(),
    val error: String? = null,
    val viewMode: HistoryViewMode = HistoryViewMode.DAY,
    val selectedDate: LocalDate = LocalDate.now(),
    val backStack: List<Pair<HistoryViewMode, LocalDate>> = emptyList(),
    val coachmarkShown: Boolean = false,
    /**
     * Счётчик команд прокрутки к [selectedDate]. Инкрементируется ТОЛЬКО при явной
     * навигации (пинч/таб/тап/выбор даты/сброс) — view скроллит при его изменении.
     * Возврат на экран (restore listState) счётчик не трогает → точная пиксельная
     * позиция сохраняется, а не перескролливается к якорю.
     */
    val scrollTick: Long = 0L,
    /** Раскладка (дерево/строки). Персистится в SettingsStorage. */
    val layout: HistoryLayout = HistoryLayout.TREE,
    /**
     * Фильтр по видам активности (`type_activ_id`), мультивыбор.
     * Пустое множество = показывать все виды. Влияет и на агрегаты периодов.
     */
    val selectedTypeIds: Set<Int> = emptySet(),
    /** Поле сортировки в строчной раскладке. */
    val sortBy: HistorySort = HistorySort.DATE,
    /** Направление сортировки: false = по убыванию (новые/большие сверху). */
    val sortAsc: Boolean = false,
)
