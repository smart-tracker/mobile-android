package com.example.smarttracker.data.remote.dto

import com.example.smarttracker.domain.model.AuthResult
import com.google.gson.annotations.SerializedName

/**
 * DTO для ответа эндпоинтов, возвращающих токены:
 * - POST /auth/verify-email
 * - POST /auth/login
 * - POST /auth/refresh
 *
 * Соответствует схеме TokenResponse на бэкенде.
 */
data class AuthResponseDto(
    @SerializedName("access_token")  val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("token_type")    val tokenType: String = "bearer"
)

fun AuthResponseDto.toDomain(): AuthResult = AuthResult(
    accessToken  = accessToken,
    refreshToken = refreshToken,
    tokenType    = tokenType
)
