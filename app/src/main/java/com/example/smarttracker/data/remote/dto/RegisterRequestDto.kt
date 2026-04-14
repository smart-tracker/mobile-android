package com.example.smarttracker.data.remote.dto

import com.example.smarttracker.domain.model.RegisterRequest
import com.example.smarttracker.domain.model.toRoleId
import com.google.gson.annotations.SerializedName

/**
 * DTO для запроса POST /auth/register.
 *
 * Нюансы:
 * - username (domain) → nickname (API)
 * - birthDate (LocalDate) → строка "yyyy-MM-dd" (LocalDate.toString() даёт ISO-формат)
 * - gender (enum) → lowercase строка "male" / "female"
 * - confirmPassword ВКЛЮЧЁН: бэкенд (FastAPI/UserCreate) его валидирует обязательно
 * - role_ids — выбранная цель использования конвертируется в role_id из таблицы БД roles:
 *   * ATHLETE (1) → [1]
 *   * TRAINER (2) → [2]
 *   * CLUB_OWNER (3) → [3]
 *   * EXPLORING/OTHER → [1] (дефолт: goal_id=1, т.к. API требует minItems:1)
 */
data class RegisterRequestDto(
    @SerializedName("first_name")       val firstName: String,
    @SerializedName("nickname")         val username: String,
    @SerializedName("birth_date")       val birthDate: String,
    val gender: String,
    val email: String,
    val password: String,
    @SerializedName("confirm_password") val confirmPassword: String,
    @SerializedName("goal_ids")         val goalIds: List<Int>
)

fun RegisterRequest.toDto(): RegisterRequestDto = RegisterRequestDto(
    firstName       = firstName,
    username        = username,
    birthDate       = birthDate.toString(),    // LocalDate → "yyyy-MM-dd"
    gender          = gender.name.lowercase(), // MALE → "male", FEMALE → "female"
    email           = email,
    password        = password,
    confirmPassword = confirmPassword,
    goalIds         = if (roleIds.isNotEmpty()) roleIds
                      else (purpose.toRoleId()?.let { listOf(it) } ?: listOf(1))
    // Приоритет: явный roleIds → purpose → дефолт goal_id=1.
    // Дефолт для EXPLORING/OTHER: API требует minItems:1, пустой список давал HTTP 422.
    // goal_id=1 (спортсмен) — наименее ограничивающая роль, можно сменить в профиле.
)
