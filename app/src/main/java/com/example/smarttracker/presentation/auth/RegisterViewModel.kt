package com.example.smarttracker.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarttracker.domain.model.Gender
import com.example.smarttracker.domain.model.RegisterRequest
import com.example.smarttracker.domain.model.UserPurpose
import com.example.smarttracker.domain.repository.AuthRepository
import com.example.smarttracker.domain.usecase.RegisterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val registerUseCase: RegisterUseCase,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<RegisterEvent>()
    val events: SharedFlow<RegisterEvent> = _events.asSharedFlow()

    private var cooldownJob: Job? = null

    // ── Шаг 1: Личные данные ─────────────────────────────────────────────────

    fun onFirstNameChange(value: String) =
        _state.update { it.copy(firstName = value, fieldError = null) }

    fun onUsernameChange(value: String) =
        _state.update { it.copy(username = value, fieldError = null) }

    fun onBirthDateChange(value: String) {
        // Разрешаем только цифры и точки, авто-форматирование дд.мм.гггг
        val filtered = value.filter { it.isDigit() || it == '.' }
        _state.update { it.copy(birthDate = filtered, fieldError = null) }
    }

    fun onGenderChange(gender: Gender) =
        _state.update { it.copy(gender = gender, fieldError = null) }

    // ── Шаг 2: Цель использования ────────────────────────────────────────────

    fun onPurposeChange(purpose: UserPurpose) =
        _state.update { it.copy(purpose = purpose, fieldError = null) }

    // ── Шаг 3: Безопасность и доступ ─────────────────────────────────────────

    fun onEmailChange(value: String) =
        _state.update { it.copy(email = value, fieldError = null) }

    fun onPasswordChange(value: String) =
        _state.update { it.copy(password = value, fieldError = null) }

    fun onConfirmPasswordChange(value: String) =
        _state.update { it.copy(confirmPassword = value, fieldError = null) }

    fun onTogglePasswordVisibility() =
        _state.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }

    fun onToggleConfirmPasswordVisibility() =
        _state.update { it.copy(isConfirmPasswordVisible = !it.isConfirmPasswordVisible) }

    fun onTermsAcceptedChange(accepted: Boolean) =
        _state.update { it.copy(termsAccepted = accepted, fieldError = null) }

    // ── Шаг 4: Подтверждение почты ───────────────────────────────────────────

    fun onVerificationCodeChange(value: String) =
        _state.update { it.copy(verificationCode = value, fieldError = null) }

    fun onResendCode() {
        val email = _state.value.email
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, fieldError = null) }
            authRepository.resendCode(email)
                .onSuccess { result ->
                    startCooldown(result.expiresIn)
                    _state.update { it.copy(isLoading = false) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(isLoading = false, error = error.message ?: "Ошибка при повторной отправке")
                    }
                }
        }
    }

    // ── Навигация между шагами ────────────────────────────────────────────────

    fun onNext() {
        when (_state.value.step) {
            1 -> validateStep1()
            2 -> validateStep2()
            3 -> submitRegistration()
            4 -> verifyEmail()
        }
    }

    fun onBack() {
        val currentStep = _state.value.step
        if (currentStep > 1) {
            _state.update { it.copy(step = currentStep - 1, fieldError = null, error = null) }
        } else {
            viewModelScope.launch { _events.emit(RegisterEvent.NavigateBack) }
        }
    }

    // ── Приватная логика ──────────────────────────────────────────────────────

    private fun validateStep1() {
        val s = _state.value
        val error = when {
            s.firstName.isBlank()  -> "Введите имя"
            s.username.isBlank()   -> "Введите имя пользователя"
            s.username.length < 3  -> "Имя пользователя: минимум 3 символа"
            parseBirthDate(s.birthDate) == null -> "Введите корректную дату (дд.мм.гггг)"
            s.gender == null       -> "Выберите пол"
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(fieldError = error) }
        } else {
            _state.update { it.copy(step = 2, fieldError = null) }
        }
    }

    private fun validateStep2() {
        val s = _state.value
        if (s.purpose == null) {
            _state.update { it.copy(fieldError = "Выберите цель использования") }
        } else {
            _state.update { it.copy(step = 3, fieldError = null) }
        }
    }

    private fun submitRegistration() {
        val s = _state.value
        val birthDate = parseBirthDate(s.birthDate) ?: run {
            _state.update { it.copy(fieldError = "Некорректная дата рождения") }
            return
        }
        val emailError = when {
            s.email.isBlank() -> "Введите email"
            !android.util.Patterns.EMAIL_ADDRESS.matcher(s.email).matches() -> "Некорректный формат email"
            else -> null
        }
        if (emailError != null) {
            _state.update { it.copy(fieldError = emailError) }
            return
        }
        val passwordError = when {
            s.password.length < 8 -> "Пароль: минимум 8 символов"
            s.password != s.confirmPassword -> "Пароли не совпадают"
            else -> null
        }
        if (passwordError != null) {
            _state.update { it.copy(fieldError = passwordError) }
            return
        }
        if (!s.termsAccepted) {
            _state.update { it.copy(fieldError = "Необходимо принять условия использования") }
            return
        }

        val request = RegisterRequest(
            firstName = s.firstName,
            username = s.username,
            birthDate = birthDate,
            gender = s.gender!!,
            purpose = s.purpose!!,
            email = s.email,
            password = s.password,
            confirmPassword = s.confirmPassword,
        )

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, fieldError = null) }
            registerUseCase(request)
                .onSuccess { result ->
                    startCooldown(result.expiresIn)
                    _state.update { it.copy(isLoading = false, step = 4) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(isLoading = false, error = error.message ?: "Ошибка регистрации")
                    }
                }
        }
    }

    private fun verifyEmail() {
        val s = _state.value
        if (s.verificationCode.isBlank()) {
            _state.update { it.copy(fieldError = "Введите код подтверждения") }
            return
        }
        if (s.verificationCode.length != 6) {
            _state.update { it.copy(fieldError = "Код должен содержать 6 символов") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, fieldError = null) }
            authRepository.verifyEmail(s.email, s.verificationCode)
                .onSuccess {
                    cooldownJob?.cancel()
                    _state.update { it.copy(isLoading = false) }
                    _events.emit(RegisterEvent.NavigateToHome)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(isLoading = false, error = error.message ?: "Неверный код")
                    }
                }
        }
    }

    private fun startCooldown(seconds: Int) {
        cooldownJob?.cancel()
        _state.update { it.copy(resendCooldownSeconds = seconds) }
        cooldownJob = viewModelScope.launch {
            for (remaining in seconds downTo 1) {
                delay(1000L)
                _state.update { it.copy(resendCooldownSeconds = remaining - 1) }
            }
        }
    }

    /** Парсинг даты из формата дд.мм.гггг → LocalDate */
    private fun parseBirthDate(dateStr: String): LocalDate? {
        val trimmed = dateStr.trim()
        val parts = trimmed.split(".")
        if (parts.size != 3) return null
        return try {
            val day   = parts[0].toIntOrNull() ?: return null
            val month = parts[1].toIntOrNull() ?: return null
            val year  = parts[2].toIntOrNull() ?: return null
            LocalDate.of(year, month, day)
        } catch (e: DateTimeParseException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        cooldownJob?.cancel()
    }
}
