package com.example.smarttracker.domain.model

/**
 * Результат успешной инициализации восстановления пароля.
 * Возвращается после первого шага (confirm email).
 * Contains information about verification code expiration and resend cooldown.
 */
data class ForgotPasswordResult(
    val message: String,
    val email: String,
    /** Срок действия кода верификации в секундах (обычно 600 = 10 минут) */
    val expiresIn: Int,
    /** Email, на который отправлен код (может отличаться от входного) */
    val emailSent: String? = null
)
