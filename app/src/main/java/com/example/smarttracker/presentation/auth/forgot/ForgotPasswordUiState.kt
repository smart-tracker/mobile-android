package com.example.smarttracker.presentation.auth.forgot

/**
 * UI-состояние для трёхшагового жанра password recovery flow.
 * Хранит текущий шаг, данные пользователя и состояние ошибок/загрузки.
 */
data class ForgotPasswordUiState(
    // Текущий шаг (1-3)
    val currentStep: Int = 1,
    
    // Шаг 1: Ввод email
    val email: String = "",
    val emailError: String? = null,
    
    // Шаг 2: Ввод кода верификации
    val verificationCode: String = "",
    val verificationCodeError: String? = null,

    // Шаг 3: Ввод новых паролей
    val newPassword: String = "",
    val newPasswordError: String? = null,
    val newPasswordVisibility: Boolean = false,
    
    val confirmPassword: String = "",
    val confirmPasswordError: String? = null,
    val confirmPasswordVisibility: Boolean = false,
    
    // Таймер для resend-кода (остаток секунд)
    val resendCodeCooldown: Int = 0,
    
    // Общее состояние
    val isLoading: Boolean = false,
    val generalError: String? = null,
    
    // Код верификации (для показа email на шаге 3)
    val verificationCodeExpiresIn: Int = 600, // 10 минут
    val codesRemainingSends: Int = 0
)
