package com.example.smarttracker.presentation.auth

/**
 * События пользователя для password recovery flow.
 * Отправляются из UI-компонентов в ViewModel.
 */
sealed class ForgotPasswordEvent {
    // Шаг 1: Email
    data class OnEmailChanged(val email: String) : ForgotPasswordEvent()
    object OnContinueFromStep1 : ForgotPasswordEvent()
    
    // Шаг 2: Новый пароль
    data class OnNewPasswordChanged(val password: String) : ForgotPasswordEvent()
    data class OnConfirmPasswordChanged(val password: String) : ForgotPasswordEvent()
    object OnToggleNewPasswordVisibility : ForgotPasswordEvent()
    object OnToggleConfirmPasswordVisibility : ForgotPasswordEvent()
    object OnContinueFromStep2 : ForgotPasswordEvent()
    
    // Шаг 3: Код верификации
    data class OnVerificationCodeChanged(val code: String) : ForgotPasswordEvent()
    object OnResendCode : ForgotPasswordEvent()
    object OnResetPassword : ForgotPasswordEvent()
    
    // Навигация
    object OnBackPressed : ForgotPasswordEvent()
}
