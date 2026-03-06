package com.example.smarttracker.data.remote.dto

import com.example.smarttracker.domain.model.RegisterRequest
import com.google.gson.annotations.SerializedName

/**
 * DTO для запроса POST /auth/register.
 *
 * Нюансы:
 * - username (domain) → nickname (API)
 * - birthDate (LocalDate) → строка "yyyy-MM-dd" (LocalDate.toString() даёт ISO-формат)
 * - gender (enum) → lowercase строка "male" / "female"
 * - confirmPassword ВКЛЮЧЁН: бэкенд (FastAPI/UserCreate) его валидирует обязательно
 * - purpose — НЕ включён: API-поля нет, решение на стороне клиента
 */
data class RegisterRequestDto(
    @SerializedName("first_name")       val firstName: String,
    @SerializedName("nickname")         val username: String,
    @SerializedName("birth_date")       val birthDate: String,
    val gender: String,
    val email: String,
    val password: String,
    @SerializedName("confirm_password") val confirmPassword: String
)

fun RegisterRequest.toDto(): RegisterRequestDto = RegisterRequestDto(
    firstName       = firstName,
    username        = username,
    birthDate       = birthDate.toString(),    // LocalDate → "yyyy-MM-dd"
    gender          = gender.name.lowercase(), // MALE → "male", FEMALE → "female"
    email           = email,
    password        = password,
    confirmPassword = confirmPassword
)
