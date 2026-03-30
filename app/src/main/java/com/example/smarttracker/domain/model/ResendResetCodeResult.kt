package com.example.smarttracker.domain.model

/**
 * Результат повторной отправки кода верификации при восстановлении пароля.
 *
 * Backend POST /password-reset/resend-verify-code возвращает пустой объект `{}`.
 * Поле `message` заполняется дефолтным значением на стороне маппера.
 */
data class ResendResetCodeResult(
    val message: String,
)
