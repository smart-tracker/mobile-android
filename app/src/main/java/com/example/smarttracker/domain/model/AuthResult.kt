package com.example.smarttracker.domain.model

/**
 * Результат успешной верификации email.
 * Соответствует ответу POST /auth/verify-email:
 * {
 *   "access_token": "...",
 *   "refresh_token": "...",
 *   "token_type": "bearer"
 * }
 *
 * Токены сохраняются в EncryptedSharedPreferences (МОБ-2.4).
 * После получения пользователь переходит на главный экран.
 */
data class AuthResult(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "bearer"
)