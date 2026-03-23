package com.example.smarttracker.presentation.auth.login

/** Одноразовые события из LoginViewModel в UI */
sealed interface LoginEvent {
    /** Логин завершён — переход на главный экран */
    data object NavigateToHome : LoginEvent
    /** Переход на экран регистрации (нет аккаунта) */
    data object NavigateToRegister : LoginEvent
    /** Переход на восстановление пароля */
    data object NavigateToPasswordRecovery : LoginEvent
}
