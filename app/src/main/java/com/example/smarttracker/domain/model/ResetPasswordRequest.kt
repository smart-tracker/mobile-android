package com.example.smarttracker.domain.model

/**
 * Запрос на завершение процесса восстановления пароля.
 * Отправляется на третьем шаге: пользователь вводит код и новый пароль.
 */
data class ResetPasswordRequest(
    val email: String,
    val code: String,
    val newPassword: String,
    val confirmPassword: String
)
