package com.example.smarttracker.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.example.smarttracker.domain.model.ResendResetCodeResult

/**
 * DTO для ответа на запрос resendResetCode (POST /auth/resend-reset-code).
 * Возвращается когда пользователь нажал "Отправить код повторно".
 *
 * Аналог ResendCodeResponseDto, но для password recovery flow.
 */
data class ResendResetCodeResponseDto(
    @SerializedName("message")
    val message: String,
    
    @SerializedName("expires_at")
    val expiresAt: String,
    
    @SerializedName("remaining_seconds")
    val remainingSeconds: Int? = null
) {
    fun toDomain(): ResendResetCodeResult = ResendResetCodeResult(
        message = message,
        expiresAt = expiresAt,
        remainingSeconds = remainingSeconds
    )
}
