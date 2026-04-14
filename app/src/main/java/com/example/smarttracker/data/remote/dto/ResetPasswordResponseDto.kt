package com.example.smarttracker.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.example.smarttracker.domain.model.ResetPasswordResult

/**
 * DTO для ответа на запрос resetPassword (POST /password-reset/confirm).
 * По OpenAPI endpoint возвращает TokenResponse.
 */
data class ResetPasswordResponseDto(
    @SerializedName("access_token")
    val accessToken: String,

    @SerializedName("refresh_token")
    val refreshToken: String,

    @SerializedName("token_type")
    val tokenType: String = "bearer"
)

fun ResetPasswordResponseDto.toDomain(): ResetPasswordResult = ResetPasswordResult(
    message         = "Пароль успешно изменён",
    success         = true,
    redirectToLogin = false // токены сохраняются репозиторием до вызова toDomain()
)
