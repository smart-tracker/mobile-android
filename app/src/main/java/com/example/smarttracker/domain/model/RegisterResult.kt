package com.example.smarttracker.domain.model

/**
 * Результат успешной отправки формы регистрации.
 * Соответствует ответу POST /auth/register:
 * {
 *   "message": "...",
 *   "email": "user@example.com",
 *   "expires_in": 600
 * }
 *
 * После получения этого результата пользователь переходит
 * на экран ввода кода подтверждения.
 */
data class RegisterResult(
    val email: String,
    val expiresIn: Int  // секунды до истечения кода — для таймера на UI
)