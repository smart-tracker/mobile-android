package com.example.smarttracker.presentation.workout.start

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarttracker.domain.model.WorkoutType
import com.example.smarttracker.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * ViewModel экрана начала / активной тренировки.
 *
 * Загружает список типов тренировок из репозитория при инициализации.
 * Управляет выбором типа и переключением между состояниями «до» и «во время» тренировки.
 */
@HiltViewModel
class WorkoutStartViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
) : ViewModel() {

    data class UiState(
        /** Текущая дата в формате "DD.MM.YYYY (День недели)" */
        val currentDate: String = formatCurrentDate(),
        /** Список доступных типов тренировок, подгружается с сервера */
        val workoutTypes: List<WorkoutType> = emptyList(),
        /** Выбранный тип — устанавливается после загрузки или после клика по иконке */
        val selectedType: WorkoutType? = null,
        /** true пока список типов загружается */
        val isTypesLoading: Boolean = true,
        /** true когда тренировка запущена — переключает кнопку "Начать" на "Пауза"+"Завершить" */
        val isTracking: Boolean = false,
        /** Сообщение об ошибке загрузки типов тренировок (null = нет ошибки) */
        val errorMessage: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        loadWorkoutTypes()
    }

    private fun loadWorkoutTypes() {
        viewModelScope.launch {
            _state.update { it.copy(isTypesLoading = true) }
            workoutRepository.getWorkoutTypes()
                .onSuccess { types ->
                    _state.update { it.copy(
                        workoutTypes = types,
                        // Первый тип из списка выбран по умолчанию
                        selectedType = types.firstOrNull(),
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

    /** Нажатие «Начать тренировку» — запускает трекинг */
    fun onStartWorkoutClick() {
        _state.update { it.copy(isTracking = true) }
    }

    /** Пользователь кликнул на иконку типа — меняет выбранный тип */
    fun onWorkoutTypeSelected(type: WorkoutType) {
        _state.update { it.copy(selectedType = type) }
    }

    /** Нажатие «Пауза» — приостанавливает трекинг, возвращает кнопку "Начать" */
    fun onPauseClick() {
        _state.update { it.copy(isTracking = false) }
    }

    /** Нажатие «Завершить» — сбрасывает состояние к начальному (экран сводки — будущее) */
    fun onFinishClick() {
        _state.update { it.copy(isTracking = false) }
    }

    companion object {
        /**
         * Форматирует текущую дату в строку "25.02.2026 (Среда)".
         * java.time доступен нативно при minSdk=26 — desugaring не нужен.
         */
        fun formatCurrentDate(): String {
            // Принудительно используем русскую локаль, чтобы день недели всегда был на русском
            val locale = Locale("ru")
            val date = LocalDate.now()
            val dateStr = date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy", locale))
            // EEEE даёт "среда" — капитализируем первую букву
            val dayStr = date.format(DateTimeFormatter.ofPattern("EEEE", locale))
                .replaceFirstChar { it.uppercase(locale) }
            return "$dateStr ($dayStr)"
        }
    }
}
