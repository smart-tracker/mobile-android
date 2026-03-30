package com.example.smarttracker.data.remote.dto

import com.example.smarttracker.domain.model.ResendResetCodeResult

/**
 * DTO для ответа на запрос resendResetCode (POST /password-reset/resend-verify-code).
 * Backend возвращает пустой объект `{}` при успехе,
 * поэтому message nullable с дефолтом.
 */
data class ResendResetCodeResponseDto(
    val message: String? = null
)

fun ResendResetCodeResponseDto.toDomain(): ResendResetCodeResult = ResendResetCodeResult(
    message = message ?: "Код отправлен повторно"
)
