package com.example.smarttracker.presentation.auth.forgot

/**
 * События пользователя для password recovery flow.
 * Отправляются из UI-компонентов в ViewModel.
 */
sealed class ForgotPasswordEvent {
    // Шаг 1: Email
    data class OnEmailChanged(val email: String) : ForgotPasswordEvent()
    object OnContinueFromStep1 : ForgotPasswordEvent()
    
    // Шаг 2: Код верификации
    data class OnVerificationCodeChanged(val code: String) : ForgotPasswordEvent()
    object OnResendCode : ForgotPasswordEvent()
    object OnContinueFromStep2 : ForgotPasswordEvent()

    // Шаг 3: Новый пароль
    data class OnNewPasswordChanged(val password: String) : ForgotPasswordEvent()
    data class OnConfirmPasswordChanged(val password: String) : ForgotPasswordEvent()
    object OnToggleNewPasswordVisibility : ForgotPasswordEvent()
    object OnToggleConfirmPasswordVisibility : ForgotPasswordEvent()
    object OnResetPassword : ForgotPasswordEvent()
    
    // Навигация
    object OnBackPressed : ForgotPasswordEvent()
    object NavigateToLoginAfterReset : ForgotPasswordEvent()
    object NavigateToLoginFromBack : ForgotPasswordEvent()
    /** Авто-вход после сброса пароля — токены уже сохранены в TokenStorage */
    object NavigateToHomeAfterReset : ForgotPasswordEvent()
}
