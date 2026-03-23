package com.example.smarttracker.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.example.smarttracker.domain.model.ResetPasswordResult

/**
 * DTO для ответа на запрос resetPassword (POST /auth/reset-password).
 * Возвращается когда пользователь успешно ввёл код и новый пароль.
 */
data class ResetPasswordResponseDto(
    @SerializedName("message")
    val message: String,
    
    @SerializedName("success")
    val success: Boolean
) {
    fun toDomain(): ResetPasswordResult = ResetPasswordResult(
        message = message,
        success = success,
        redirectToLogin = true
    )
}
