package com.example.smarttracker.presentation.menu.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarttracker.domain.model.Gender
import com.example.smarttracker.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject

/**
 * ViewModel экрана редактирования профиля.
 *
 * При создании загружает данные из кэша [UserProfileCache] (через [AuthRepository.getUserInfo])
 * — без сетевого запроса. После успешного сохранения PATCH /user/edit кэш обновляется
 * в [AuthRepositoryImpl.updateProfile], а [ProfileEditEvent.NavigateBack] уводит назад.
 */
@HiltViewModel
class ProfileEditViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileEditUiState())
    val state: StateFlow<ProfileEditUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ProfileEditEvent>()
    val events: SharedFlow<ProfileEditEvent> = _events.asSharedFlow()

    init {
        loadFromCache()
    }

    /**
     * Загружает текущие данные профиля из кэша.
     * Кэш всегда заполнен после логина — сетевой запрос не нужен.
     * При отсутствии кэша (не должно случиться в штатном потоке) выполняет GET /user/.
     */
    private fun loadFromCache() {
        viewModelScope.launch {
            authRepository.getUserInfo()
                .onSuccess { user ->
                    // birthDate LocalDate → 8-цифровая строка "DDMMYYYY"
                    val d = user.birthDate.dayOfMonth.toString().padStart(2, '0')
                    val m = user.birthDate.monthValue.toString().padStart(2, '0')
                    val y = user.birthDate.year.toString()
                    _state.update {
                        it.copy(
                            isLoading  = false,
                            firstName  = user.firstName,
                            lastName   = user.lastName  ?: "",
                            middleName = user.middleName ?: "",
                            username   = user.username,
                            birthDate  = "$d$m$y",
                            gender     = when (user.gender) {
                                Gender.MALE   -> "male"
                                Gender.FEMALE -> "female"
                            },
                            height = user.height?.let { h -> "%.0f".format(h) } ?: "",
                            weight = user.weight?.let { w -> "%.0f".format(w) } ?: "",
                        )
                    }
                }
                .onFailure {
                    _state.update { it.copy(isLoading = false) }
                }
        }
    }

    fun onFirstNameChange(v: String)  = _state.update { it.copy(firstName = v) }
    fun onLastNameChange(v: String)   = _state.update { it.copy(lastName = v) }
    fun onMiddleNameChange(v: String) = _state.update { it.copy(middleName = v) }
    fun onUsernameChange(v: String)   = _state.update { it.copy(username = v) }

    fun onBirthDateChange(v: String) {
        if (v.length <= 8 && v.all { it.isDigit() }) {
            _state.update { it.copy(birthDate = v) }
        }
    }

    fun onGenderToggle() = _state.update {
        it.copy(gender = if (it.gender == "male") "female" else "male")
    }

    fun onHeightChange(v: String) = _state.update { it.copy(height = v) }
    fun onWeightChange(v: String) = _state.update { it.copy(weight = v) }

    fun onSave() {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, errorMessage = null) }
            val s = _state.value

            // "DDMMYYYY" → ISO 8601 "YYYY-MM-DD"
            val isoDate = runCatching {
                if (s.birthDate.length == 8) {
                    val day   = s.birthDate.substring(0, 2).toInt()
                    val month = s.birthDate.substring(2, 4).toInt()
                    val year  = s.birthDate.substring(4, 8).toInt()
                    LocalDate.of(year, month, day).toString()
                } else null
            }.getOrNull()

            authRepository.updateProfile(
                firstName  = s.firstName.takeIf { it.isNotBlank() },
                lastName   = s.lastName.takeIf  { it.isNotBlank() },
                middleName = s.middleName.takeIf { it.isNotBlank() },
                birthDate  = isoDate,
                weight     = s.weight.toFloatOrNull(),
                height     = s.height.toFloatOrNull(),
                gender     = s.gender,
                nickname   = s.username.takeIf { it.isNotBlank() },
            ).onSuccess {
                _state.update { it.copy(isSaving = false) }
                _events.emit(ProfileEditEvent.NavigateBack)
            }.onFailure { e ->
                _state.update {
                    it.copy(isSaving = false, errorMessage = errorMessage(e, "Ошибка сохранения"))
                }
            }
        }
    }

    fun onDeleteAccountClick() = _state.update { it.copy(showDeleteConfirmDialog = true) }
    fun onDismissDeleteDialog() = _state.update { it.copy(showDeleteConfirmDialog = false) }

    fun onConfirmDelete() {
        viewModelScope.launch {
            _state.update { it.copy(showDeleteConfirmDialog = false, isDeleting = true) }
            authRepository.deleteAccount()
                .onSuccess { _events.emit(ProfileEditEvent.AccountDeleted) }
                .onFailure { e ->
                    _state.update {
                        it.copy(isDeleting = false, errorMessage = errorMessage(e, "Ошибка удаления аккаунта"))
                    }
                }
        }
    }

    /**
     * Переводит техническое исключение в читаемый текст для UI.
     * [IOException] — нет сети; остальное — серверная или неизвестная ошибка.
     */
    private fun errorMessage(e: Throwable, fallback: String): String = when (e) {
        is IOException -> "Нет соединения. Проверьте интернет и попробуйте снова."
        else           -> e.message?.takeIf { it.isNotBlank() } ?: fallback
    }
}

sealed class ProfileEditEvent {
    data object NavigateBack    : ProfileEditEvent()
    data object AccountDeleted  : ProfileEditEvent()
}
