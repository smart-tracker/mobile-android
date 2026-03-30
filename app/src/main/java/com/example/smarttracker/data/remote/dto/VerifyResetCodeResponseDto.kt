package com.example.smarttracker.data.remote.dto

/**
 * DTO ответа для проверки кода восстановления пароля (POST /password-reset/verify-code).
 * Backend возвращает пустой объект `{}` при успехе.
 */
data class VerifyResetCodeResponseDto(
    val message: String? = null
)
