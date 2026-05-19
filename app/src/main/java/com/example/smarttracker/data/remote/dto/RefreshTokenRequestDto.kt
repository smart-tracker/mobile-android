package com.example.smarttracker.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Тело запроса POST /auth/refresh.
 * FastAPI-роут использует Body(...) — поле передаётся в JSON-теле, не query-параметром.
 */
data class RefreshTokenRequestDto(
    @SerializedName("refresh_token") val refreshToken: String
)
