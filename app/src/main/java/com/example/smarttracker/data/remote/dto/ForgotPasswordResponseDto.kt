package com.example.smarttracker.data.remote.dto

import com.example.smarttracker.domain.model.ForgotPasswordResult

/**
 * DTO для ответа на запрос initiateForgotPassword (POST /password-reset/request).
 * Backend возвращает пустой объект `{}` при успехе,
 * поэтому все поля nullable с дефолтами.
 */
data class ForgotPasswordResponseDto(
    val message: String? = null
)

fun ForgotPasswordResponseDto.toDomain(): ForgotPasswordResult = ForgotPasswordResult(
    message = message ?: "Код отправлен на email"
)
