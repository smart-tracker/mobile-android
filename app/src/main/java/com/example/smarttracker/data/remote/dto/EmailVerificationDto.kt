package com.example.smarttracker.data.remote.dto

/**
 * DTO для запроса POST /auth/verify-email.
 *
 * Соответствует схеме EmailVerificationCode на бэкенде.
 * code — 6-символьная строка (min_length=6, max_length=6 на бэкенде).
 *
 * Mapper не нужен — объект создаётся напрямую в AuthRepositoryImpl.
 */
data class EmailVerificationDto(
    val email: String,
    val code: String
)
