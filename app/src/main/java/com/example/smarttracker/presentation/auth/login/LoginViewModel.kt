package com.example.smarttracker.presentation.auth.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarttracker.domain.usecase.LoginUseCase
import com.example.smarttracker.utils.ApiErrorHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * МОБ-3.2 — ViewModel экрана входа.
 *
 * Ответственность:
 *   1. Управление состоянием формы (email, password, isLoading, ошибки)
 *   2. Валидация полей
 *   3. Вызов LoginUseCase для проверки на клиенте
 *   4. На успех вызов authRepository.login() для сохранения токенов
 *   5. Эмит событий навигации (NavigateToHome, NavigateToRegister, и т.д.)
 *
 * Обработка ошибок:
 *   - Валидационные ошибки (пустые поля) → fieldError (в UI красное выделение)
 *   - API-ошибки (401, 400, 500) → errorMessage (через Snackbar/Dialog)
 *   - ApiErrorHandler парсит JSON из HTTP-ответа и переводит на русский
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<LoginEvent>()
    val events: SharedFlow<LoginEvent> = _events.asSharedFlow()

    // ── Обработка ввода ──────────────────────────────────────────────────────

    fun onEmailChange(value: String) =
        _state.update { it.copy(email = value) }

    fun onPasswordChange(value: String) =
        _state.update { it.copy(password = value) }

    fun onTogglePasswordVisibility() =
        _state.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }

    // ── Основная логика ──────────────────────────────────────────────────────

    /**
     * Попытка входа. Вызывается при нажатии на кнопку "Войти".
     *
     * Процесс:
     *   1. Валидация через LoginUseCase (проверка формата email, длины пароля)
     *   2. На успех вызов api.login() — сохранение токенов в TokenStorage
     *   3. На ошибку вывод errorMessage в UI
     *   4. На успех эмит NavigateToHome → NavGraph переходит на HomeScreen
     */
    fun onSubmitLogin() {
        val s = _state.value

        // — Локальная проверка для быстрого фидбека ——————————

        val validationError = validateForm()
        if (validationError != null) {
            _state.update { it.copy(errorMessage = validationError) }
            return
        }

        // — Валидация через UseCase (API-формат) –———————————

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            loginUseCase(s.email, s.password)
                .onSuccess { _ ->
                    _state.update { it.copy(isLoading = false) }
                    _events.emit(LoginEvent.NavigateToHome)
                }
                .onFailure { error ->
                    val errorMessage = ApiErrorHandler.getErrorMessage(error)
                    _state.update {
                        it.copy(isLoading = false, errorMessage = errorMessage)
                    }
                }
        }
    }

    // ── Навигация ────────────────────────────────────────────────────────────

    fun onNavigateToRegister() {
        viewModelScope.launch {
            _events.emit(LoginEvent.NavigateToRegister)
        }
    }

    fun onNavigateToPasswordRecovery() {
        viewModelScope.launch {
            _events.emit(LoginEvent.NavigateToPasswordRecovery)
        }
    }

    // ── Приватная валидация ──────────────────────────────────────────────────

    private fun validateForm(): String? {
        val s = _state.value

        if (s.email.isBlank()) {
            return "Введите email"
        }

        if (s.password.isBlank()) {
            return "Введите пароль"
        }

        if (s.password.length < 8) {
            return "Пароль должен содержать минимум 8 символов"
        }

        return null
    }

    // ── Проверка заполненности для disabling кнопки ──────────────────────────

    fun isFormValid(): Boolean {
        val s = _state.value
        return s.email.isNotBlank() &&
               s.password.isNotBlank() &&
               s.password.length >= 8
    }
}
