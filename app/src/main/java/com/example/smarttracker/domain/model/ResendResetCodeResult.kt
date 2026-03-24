package com.example.smarttracker.domain.model

/**
 * Результат повторной отправки кода верификации при восстановлении пароля.
 * Аналог ResendResult, но для password recovery flow.
 */
data class ResendResetCodeResult(
    val message: String,
    val expiresAt: String? = null,
    /** Оставшееся время до следующей отправки в секундах (может быть null) */
    val remainingSeconds: Int? = null
)
