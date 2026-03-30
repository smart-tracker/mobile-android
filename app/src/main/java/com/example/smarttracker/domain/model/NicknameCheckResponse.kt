package com.example.smarttracker.domain.model

/**
 * МОБ-... — Ответ на проверку уникальности nickname.
 * Возвращается от API endpoint POST /auth/check-nickname
 */
data class NicknameCheckResponse(
    val nickname: String,
    val isAvailable: Boolean,
    val message: String,
)
