package com.example.smarttracker.presentation.auth.register

/** Одноразовые события из RegisterViewModel в UI */
sealed interface RegisterEvent {
    /** Регистрация завершена — переход на главный экран */
    data object NavigateToHome : RegisterEvent
    /** Назад с первого шага (закрыть экран) */
    data object NavigateBack : RegisterEvent
}
