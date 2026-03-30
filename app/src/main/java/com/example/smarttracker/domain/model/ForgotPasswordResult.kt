package com.example.smarttracker.domain.model

/**
 * Результат успешной инициализации восстановления пароля.
 * Возвращается после первого шага (confirm email).
 *
 * Backend POST /password-reset/request возвращает пустой объект `{}`.
 * Поле `message` заполняется дефолтным значением на стороне маппера.
 */
data class ForgotPasswordResult(
    val message: String,
)
