package com.example.smarttracker.data.remote.dto

/**
 * Вспомогательные DTO для тел HTTP-запросов:
 * - ResendEmailDto  → POST /auth/resend-code
 * - LoginRequestDto → POST /auth/login
 *
 * POST /auth/refresh не требует DTO — там query-параметр (см. AuthApiService).
 */

/** Тело запроса POST /auth/resend-code. Соответствует схеме EmailVerificationRequest на бэкенде. */
data class ResendEmailDto(
    val email: String
)

/** Тело запроса POST /auth/login. Соответствует схеме UserLogin на бэкенде. */
data class LoginRequestDto(
    val email: String,
    val password: String
)
