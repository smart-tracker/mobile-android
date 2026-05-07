package com.example.smarttracker.presentation.menu.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarttracker.domain.model.Gender
import com.example.smarttracker.domain.repository.AuthRepository
import com.example.smarttracker.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * ViewModel экрана профиля.
 *
 * При создании сразу запрашивает данные пользователя через [AuthRepository.getUserInfo]
 * (GET /user/ — Bearer-токен добавляется интерцептором автоматически).
 *
 * Ответственность:
 *   1. Загрузка профиля из сети.
 *   2. Форматирование полей для отображения (дата, пол, числа).
 *   3. Проброс состояния загрузки и ошибки в UI.
 *
 * Почему вызываем репозиторий напрямую, без UseCase:
 *   Операция тривиальна — один вызов API без валидации и побочных эффектов.
 *   Отдельный UseCase был бы бойлерплейтом без добавленной ценности.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val workoutRepository: WorkoutRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    // Формат даты, совпадающий с тем, что ожидает дизайн: "ДД.ММ.ГГГГ"
    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    init {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }

            authRepository.getUserInfo()
                .onSuccess { user ->
                    applyUser(user)
                    _state.update { it.copy(isLoading = false) }
                    // После успешной загрузки профиля — пытаемся получить историю тренировок
                    // и вычислить дату последней тренировки (максимум по полю date).
                    // Ошибки при загрузке истории не прерывают отображение профиля.
                    workoutRepository.getTrainingHistory().onSuccess { list ->
                        val lastDate = list.maxByOrNull { it.date }?.date
                        _state.update { cur -> cur.copy(lastTrainingDate = lastDate?.format(dateFormatter)) }
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isLoading    = false,
                            errorMessage = e.message ?: "Не удалось загрузить профиль",
                        )
                    }
                }
        }
    }

    /**
     * Тихое обновление полей профиля из кэша — без индикатора загрузки.
     * Вызывается при возврате с экрана редактирования, чтобы показать актуальные данные.
     * Кэш уже обновлён в [AuthRepositoryImpl.updateProfile] до навигации назад.
     */
    fun refreshFromCache() {
        viewModelScope.launch {
            authRepository.getUserInfo().onSuccess { user -> applyUser(user) }
        }
    }

    /** Применяет данные [User] к состоянию без изменения [ProfileUiState.isLoading]. */
    private fun applyUser(user: com.example.smarttracker.domain.model.User) {
        _state.update {
            it.copy(
                firstName  = user.firstName,
                lastName   = user.lastName,
                middleName = user.middleName,
                username   = "@${user.username}",
                birthDate  = user.birthDate.format(dateFormatter),
                gender     = when (user.gender) {
                    Gender.MALE   -> "Мужской"
                    Gender.FEMALE -> "Женский"
                },
                weight = user.weight?.let { w -> "%.0f".format(w) },
                height = user.height?.let { h -> "%.0f".format(h) },
                errorMessage = null,
            )
        }
    }
}
