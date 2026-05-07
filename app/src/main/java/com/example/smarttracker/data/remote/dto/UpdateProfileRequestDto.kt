package com.example.smarttracker.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * DTO тела запроса PATCH /user/edit.
 *
 * Все поля nullable — бэкенд применяет только те, что не null.
 * [birthDate] передаётся в формате ISO 8601 "YYYY-MM-DD".
 * [gender] — строка "male" или "female".
 * [nickname] — никнейм без символа «@».
 */
data class UpdateProfileRequestDto(
    @SerializedName("first_name")  val firstName: String?,
    @SerializedName("last_name")   val lastName: String?,
    @SerializedName("middle_name") val middleName: String?,
    @SerializedName("birth_date")  val birthDate: String?,
    val weight: Float?,
    val height: Float?,
    val gender: String?,
    val nickname: String?,
)
