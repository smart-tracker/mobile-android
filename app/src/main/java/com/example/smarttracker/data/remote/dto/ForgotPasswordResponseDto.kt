package com.example.smarttracker.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.example.smarttracker.domain.model.ForgotPasswordResult

/**
 * DTO для ответа на запрос initiateForgotPassword (POST /auth/forgot-password).
 * Возвращается когда пользователь вводит email для восстановления пароля.
 *
 * Отличия от похожего RegisterResultDto:
 * - Нет user_id, JWT токенов (только код верификации отправляется на email)
 * - debug_code присутствует только при DEBUG=true на бэкенде
 */
data class ForgotPasswordResponseDto(
    @SerializedName("message")
    val message: String,
    
    @SerializedName("email")
    val email: String,
    
    @SerializedName("expires_in")
    val expiresIn: Int,
    
    /** Только для DEBUG режима - обычно не должен возвращаться в production */
    @SerializedName("debug_code")
    val debugCode: String? = null
) {
    fun toDomain(): ForgotPasswordResult = ForgotPasswordResult(
        message = message,
        email = email,
        expiresIn = expiresIn,
        emailSent = email
    )
}
