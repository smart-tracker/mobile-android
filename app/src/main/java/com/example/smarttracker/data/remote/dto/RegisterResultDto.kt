package com.example.smarttracker.data.remote.dto

import com.example.smarttracker.domain.model.RegisterResult
import com.google.gson.annotations.SerializedName

/**
 * DTO для ответа POST /auth/register.
 *
 * Бэкенд возвращает: message, email, expires_in, debug_code.
 * debug_code — намеренно НЕ включён (временное поле, убрать до прода).
 */
data class RegisterResultDto(
    val message: String,
    val email: String,
    @SerializedName("expires_in") val expiresIn: Int
)

fun RegisterResultDto.toDomain(): RegisterResult = RegisterResult(
    email     = email,
    expiresIn = expiresIn
)
