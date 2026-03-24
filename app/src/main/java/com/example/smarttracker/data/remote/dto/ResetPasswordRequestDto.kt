package com.example.smarttracker.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * DTO для отправки на запрос resetPassword (POST /password-reset/confirm).
 * Пользователь вводит код верификации и новый пароль на третьем шаге.
 */
data class ResetPasswordRequestDto(
    @SerializedName("email")
    val email: String,
    
    @SerializedName("code")
    val code: String,
    
    @SerializedName("password")
    val newPassword: String,
    
    @SerializedName("confirm_password")
    val confirmPassword: String
)
