package com.example.smarttracker.data.remote.dto

import com.example.smarttracker.domain.model.NicknameCheckResponse
import com.google.gson.annotations.SerializedName

/**
 * DTO для ответа POST /auth/check-nickname.
 *
 * Поля:
 * - nickname: String — проверяемый nickname
 * - is_available: Boolean — доступен ли nickname
 * - message: String — описание результата
 */
data class NicknameCheckResponseDto(
    val nickname: String,
    @SerializedName("is_available") val isAvailable: Boolean,
    val message: String
)

fun NicknameCheckResponseDto.toDomain(): NicknameCheckResponse = NicknameCheckResponse(
    nickname = nickname,
    isAvailable = isAvailable,
    message = message
)

/**
 * DTO для запроса POST /auth/check-nickname.
 */
data class NicknameCheckRequestDto(
    val nickname: String
)
