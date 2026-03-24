package com.example.smarttracker.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * DTO ответа для проверки кода восстановления пароля (POST /password-reset/verify-code).
 */
data class VerifyResetCodeResponseDto(
    @SerializedName("message")
    val message: String
)
