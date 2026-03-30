package com.example.smarttracker.data.remote.dto

/**
 * DTO для отправки на запрос initiateForgotPassword (POST /password-reset/request).
 * Пользователь вводит email на первом шаге password recovery.
 */
data class ForgotPasswordRequestDto(
    val email: String
)
