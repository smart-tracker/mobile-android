package com.example.smarttracker.domain.model

/**
 * Запрос на инициирование процесса восстановления пароля.
 * Отправляется на первом шаге: пользователь вводит email.
 */
data class ForgotPasswordRequest(
    val email: String
)
