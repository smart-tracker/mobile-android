package com.example.smarttracker.domain.model

/**
 * Результат успешного сброса пароля.
 * Возвращается после третьего шага (password reset confirmation).
 */
data class ResetPasswordResult(
    val message: String,
    val success: Boolean,
    /** Может содержать предложение перейти на экран логина */
    val redirectToLogin: Boolean = true
)
