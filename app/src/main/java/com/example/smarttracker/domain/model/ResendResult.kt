package com.example.smarttracker.domain.model

/**
 * Результат повторной отправки кода верификации.
 * Соответствует ответу POST /auth/resend-code:
 * {
 *   "message": "...",
 *   "expires_at": "...",
 *   "remaining_seconds": 600
 * }
 *
 * Используется для обновления таймера на экране верификации.
 */
data class ResendResult(
    val expiresIn: Int  // remaining_seconds — секунды до истечения нового кода
)
