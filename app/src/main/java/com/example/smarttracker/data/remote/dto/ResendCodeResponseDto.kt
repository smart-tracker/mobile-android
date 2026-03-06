package com.example.smarttracker.data.remote.dto

import com.example.smarttracker.domain.model.ResendResult
import com.google.gson.annotations.SerializedName

/**
 * DTO для ответа POST /auth/resend-code.
 *
 * Нюанс: оба поля Optional на бэкенде (Optional[datetime], Optional[int]),
 * поэтому remainingSeconds — Int?, а не Int.
 *
 * При маппинге в domain: null → 0 (таймер не стартует, но не крашится).
 */
data class ResendCodeResponseDto(
    val message: String,
    @SerializedName("expires_at")        val expiresAt: String?,
    @SerializedName("remaining_seconds") val remainingSeconds: Int?
)

fun ResendCodeResponseDto.toDomain(): ResendResult = ResendResult(
    expiresIn = remainingSeconds ?: 0
)
