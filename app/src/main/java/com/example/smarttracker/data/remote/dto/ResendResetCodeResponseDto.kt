package com.example.smarttracker.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.example.smarttracker.domain.model.ResendResetCodeResult

/**
 * DTO для ответа на запрос resendResetCode (POST /password-reset/resend-verify-code).
 * Возвращается когда пользователь нажал "Отправить код повторно".
 */
data class ResendResetCodeResponseDto(
    @SerializedName("message")
    val message: String
)

fun ResendResetCodeResponseDto.toDomain(): ResendResetCodeResult = ResendResetCodeResult(
    message = message
)
