package com.example.smarttracker.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.example.smarttracker.domain.model.ForgotPasswordResult

/**
 * DTO для ответа на запрос initiateForgotPassword (POST /password-reset/request).
 * Возвращается когда пользователь вводит email для восстановления пароля.
 */
data class ForgotPasswordResponseDto(
    @SerializedName("message")
    val message: String
)

fun ForgotPasswordResponseDto.toDomain(): ForgotPasswordResult = ForgotPasswordResult(
    message = message
)
