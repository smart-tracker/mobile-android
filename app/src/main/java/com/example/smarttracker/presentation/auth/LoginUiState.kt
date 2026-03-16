package com.example.smarttracker.presentation.auth

/**
 * UI-состояние экрана входа.
 *
 * Состояние разбито на логические части:
 * - Данные формы (email, password)
 * - Состояние загрузки (isLoading для индикатора прогресса)
 * - Ошибки (errorMessage для отображения через Snackbar или Dialog)
 * - Видимость пароля (для иконки "показать/скрыть")
 * - Навигация (navigateToHome — одноразовое событие через SharedFlow)
 */
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isPasswordVisible: Boolean = false,
    val navigateToHome: Boolean = false
)
