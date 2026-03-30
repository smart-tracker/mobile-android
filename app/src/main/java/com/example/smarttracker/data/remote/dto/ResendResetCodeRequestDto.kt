package com.example.smarttracker.data.remote.dto

/**
 * DTO для отправки на запрос resendResetCode (POST /password-reset/resend-verify-code).
 * Пользователь нажал "Отправить код повторно" на втором шаге.
 */
data class ResendResetCodeRequestDto(
    val email: String
)
